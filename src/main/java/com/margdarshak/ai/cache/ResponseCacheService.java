package com.margdarshak.ai.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.margdarshak.ai.model.ChatCache;
import com.margdarshak.ai.model.Intent;
import com.margdarshak.ai.repository.ChatCacheRepository;
import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Two-tier cache for LLM responses (Caffeine L1 + Postgres L2, write-through).
 *
 * Intent classifier and intent handlers route their calls through this service.
 * On miss, the supplier is invoked once; the result is written to both tiers.
 *
 * Failures in the L2 tier never break the request path — we log and continue
 * with the freshly computed result.
 */
@Service
@Slf4j
public class ResponseCacheService {

    private static final String INTENT_FIELD = "intent";

    private final Cache<String, Map<String, Object>> intentCache;
    private final Cache<String, Map<String, Object>> responseCache;
    private final ChatCacheRepository repository;
    private final ObjectMapper objectMapper;
    private final CacheKeyBuilder keyBuilder;
    private final CacheProperties props;

    public ResponseCacheService(@Qualifier(CacheConfig.INTENT_CACHE) Cache<String, Map<String, Object>> intentCache,
                                @Qualifier(CacheConfig.RESPONSE_CACHE) Cache<String, Map<String, Object>> responseCache,
                                ChatCacheRepository repository,
                                ObjectMapper objectMapper,
                                CacheKeyBuilder keyBuilder,
                                CacheProperties props) {
        this.intentCache = intentCache;
        this.responseCache = responseCache;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.keyBuilder = keyBuilder;
        this.props = props;
    }

    // -------------------------- Intent classifier API --------------------------

    /**
     * Returns the cached intent for a message, or {@link Optional#empty()} if missing.
     */
    public Optional<Intent> getIntent(String message) {
        if (!props.isEnabled()) return Optional.empty();
        String key = keyBuilder.forClassifier(message);
        Map<String, Object> payload = lookup(intentCache, key);
        if (payload == null) return Optional.empty();
        Object name = payload.get(INTENT_FIELD);
        return Optional.ofNullable(name).map(Object::toString).map(Intent::fromString);
    }

    public void putIntent(String message, Intent intent) {
        if (!props.isEnabled()) return;
        String key = keyBuilder.forClassifier(message);
        Map<String, Object> payload = Map.of(INTENT_FIELD, intent.name());
        Duration ttl = Duration.ofHours(props.getTtl().getClassifierHours());
        store(intentCache, key, CacheKeyBuilder.CLASSIFIER_LABEL, message, null, payload, ttl);
    }

    // -------------------------- Intent handler API ----------------------------

    /**
     * Get-or-compute pattern for intent handlers. Returns the cached result if
     * present, otherwise invokes {@code producer}, stores the result in both
     * tiers, and returns it.
     *
     * @param dateBucket non-null for date-sensitive intents (e.g. MUHURTA), null
     *                   for date-independent intents (e.g. FESTIVAL_GUIDE).
     */
    public <T> T compute(Intent intent,
                         String message,
                         @Nullable LocalDate dateBucket,
                         Class<T> resultType,
                         Duration ttl,
                         Supplier<T> producer) {
        if (!props.isEnabled()) {
            return producer.get();
        }

        String key = keyBuilder.forResponse(intent, message, dateBucket);
        Map<String, Object> cached = lookup(responseCache, key);
        if (cached != null) {
            return objectMapper.convertValue(cached, resultType);
        }

        T fresh = producer.get();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.convertValue(fresh, Map.class);
        store(responseCache, key, intent.name(), message, dateBucket, payload, ttl);
        return fresh;
    }

    public Duration ttlFor(Intent intent) {
        return switch (intent) {
            case MUHURTA -> Duration.ofHours(props.getTtl().getMuhurtaHours());
            case FESTIVAL_GUIDE -> Duration.ofHours(props.getTtl().getFestivalHours());
            case ASTROLOGY_EXPLAINER -> Duration.ofHours(props.getTtl().getExplainerHours());
            case UNKNOWN -> Duration.ofHours(props.getTtl().getClassifierHours());
        };
    }

    // ----------------------------- internals ---------------------------------

    private Map<String, Object> lookup(Cache<String, Map<String, Object>> l1, String key) {
        Map<String, Object> hit = l1.getIfPresent(key);
        if (hit != null) {
            log.debug("Cache L1 hit key={}", key);
            recordHit(key);
            return hit;
        }
        Optional<ChatCache> row = safeFindByKey(key);
        if (row.isPresent() && row.get().getExpiresAt().isAfter(Instant.now())) {
            log.debug("Cache L2 hit key={}", key);
            l1.put(key, row.get().getPayload());
            recordHit(key);
            return row.get().getPayload();
        }
        return null;
    }

    private void store(Cache<String, Map<String, Object>> l1,
                       String key,
                       String intent,
                       String message,
                       @Nullable LocalDate dateBucket,
                       Map<String, Object> payload,
                       Duration ttl) {
        l1.put(key, payload);
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            Instant expiresAt = Instant.now().plus(ttl);
            String bucket = dateBucket != null ? dateBucket.toString() : null;
            repository.upsert(key, intent, message, bucket, payloadJson, expiresAt);
        } catch (JsonProcessingException e) {
            log.warn("Skipping L2 cache write (serialization failed) key={} err={}", key, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Skipping L2 cache write (db failure) key={} err={}", key, e.getMessage());
        }
    }

    private Optional<ChatCache> safeFindByKey(String key) {
        try {
            return repository.findByCacheKey(key);
        } catch (RuntimeException e) {
            log.warn("L2 cache lookup failed key={} err={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void recordHit(String key) {
        try {
            repository.incrementHitCount(key);
        } catch (RuntimeException e) {
            // hit counts are best-effort — never break a request because of them
            log.debug("Hit counter update failed key={} err={}", key, e.getMessage());
        }
    }
}
