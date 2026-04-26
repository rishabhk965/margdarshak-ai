package com.margdarshak.ai.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly typed view of `cache.*` properties.
 *
 * TTL values are in hours (Duration parsing keeps `application.yml` simple,
 * but plain hours are enough for our needs and easier to reason about).
 */
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {

    private boolean enabled = true;
    private String promptVersion = "v1";
    private Ttl ttl = new Ttl();
    private Caffeine caffeine = new Caffeine();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }

    public Ttl getTtl() { return ttl; }
    public void setTtl(Ttl ttl) { this.ttl = ttl; }

    public Caffeine getCaffeine() { return caffeine; }
    public void setCaffeine(Caffeine caffeine) { this.caffeine = caffeine; }

    public static class Ttl {
        private long muhurtaHours = 24;          // day-of-week context drifts daily
        private long festivalHours = 720;        // 30 days — festival rituals are evergreen
        private long explainerHours = 24;        // day-of-week context drifts daily
        private long classifierHours = 720;      // 30 days — intent label is phrasing-only

        public long getMuhurtaHours() { return muhurtaHours; }
        public void setMuhurtaHours(long muhurtaHours) { this.muhurtaHours = muhurtaHours; }

        public long getFestivalHours() { return festivalHours; }
        public void setFestivalHours(long festivalHours) { this.festivalHours = festivalHours; }

        public long getExplainerHours() { return explainerHours; }
        public void setExplainerHours(long explainerHours) { this.explainerHours = explainerHours; }

        public long getClassifierHours() { return classifierHours; }
        public void setClassifierHours(long classifierHours) { this.classifierHours = classifierHours; }
    }

    public static class Caffeine {
        private long maxSize = 10_000;
        private long expireAfterWriteMinutes = 60;

        public long getMaxSize() { return maxSize; }
        public void setMaxSize(long maxSize) { this.maxSize = maxSize; }

        public long getExpireAfterWriteMinutes() { return expireAfterWriteMinutes; }
        public void setExpireAfterWriteMinutes(long expireAfterWriteMinutes) {
            this.expireAfterWriteMinutes = expireAfterWriteMinutes;
        }
    }
}
