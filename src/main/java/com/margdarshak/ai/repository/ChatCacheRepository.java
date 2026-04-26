package com.margdarshak.ai.repository;

import com.margdarshak.ai.model.ChatCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface ChatCacheRepository extends JpaRepository<ChatCache, Long> {

    Optional<ChatCache> findByCacheKey(String cacheKey);

    /**
     * Idempotent upsert. The DO UPDATE branch refreshes the payload + expiry so
     * a second writer with a newer prompt version transparently overwrites a
     * stale entry, while same-version writers race-safely converge on identical
     * data.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO chat_cache
            (cache_key, intent, message, date_bucket, payload, hit_count, created_at, expires_at)
        VALUES
            (:cacheKey, :intent, :message, CAST(:dateBucket AS DATE),
             CAST(:payload AS jsonb), 0, now(), :expiresAt)
        ON CONFLICT (cache_key) DO UPDATE
            SET payload    = EXCLUDED.payload,
                expires_at = EXCLUDED.expires_at
        """, nativeQuery = true)
    int upsert(@Param("cacheKey") String cacheKey,
               @Param("intent") String intent,
               @Param("message") String message,
               @Param("dateBucket") String dateBucket,
               @Param("payload") String payload,
               @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Transactional
    @Query("update ChatCache c set c.hitCount = c.hitCount + 1 where c.cacheKey = :cacheKey")
    int incrementHitCount(@Param("cacheKey") String cacheKey);

    @Modifying
    @Transactional
    @Query("delete from ChatCache c where c.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
