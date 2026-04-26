package com.margdarshak.ai.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

/**
 * Two L1 (Caffeine) caches: one for intent-classifier results, one for
 * intent-handler payloads. They share the same configuration but are kept
 * separate so eviction of one doesn't impact the other.
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    public static final String INTENT_CACHE = "intentCache";
    public static final String RESPONSE_CACHE = "responseCache";

    @Bean(INTENT_CACHE)
    public Cache<String, Map<String, Object>> intentCache(CacheProperties props) {
        return buildCaffeine(props);
    }

    @Bean(RESPONSE_CACHE)
    public Cache<String, Map<String, Object>> responseCache(CacheProperties props) {
        return buildCaffeine(props);
    }

    private Cache<String, Map<String, Object>> buildCaffeine(CacheProperties props) {
        return Caffeine.newBuilder()
                .maximumSize(props.getCaffeine().getMaxSize())
                .expireAfterWrite(Duration.ofMinutes(props.getCaffeine().getExpireAfterWriteMinutes()))
                .recordStats()
                .build();
    }
}
