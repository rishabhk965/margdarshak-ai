package com.margdarshak.ai.cache;

import com.margdarshak.ai.repository.ChatCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Hourly sweep that removes expired rows from {@code chat_cache}.
 *
 * Caffeine handles its own L1 expiry; this job only bounds the Postgres L2 size.
 * Failures here must never affect the request path — we log and move on.
 */
@Component
@Slf4j
public class CacheCleanupJob {

    private final ChatCacheRepository repository;

    @Autowired
    public CacheCleanupJob(ChatCacheRepository repository) {
        this.repository = repository;
    }

    /** Top of every hour. Fast (single indexed DELETE), safe to run on the main scheduler. */
    @Scheduled(cron = "0 0 * * * *")
    public void purgeExpired() {
        try {
            int deleted = repository.deleteExpired(Instant.now());
            if (deleted > 0) {
                log.info("chat_cache cleanup removed {} expired rows", deleted);
            }
        } catch (RuntimeException e) {
            log.warn("chat_cache cleanup failed: {}", e.getMessage());
        }
    }
}
