package com.margdarshak.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "chat_cache")
@Getter
@Setter
@NoArgsConstructor
public class ChatCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", nullable = false, unique = true, length = 160)
    private String cacheKey;

    @Column(nullable = false, length = 50)
    private String intent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "date_bucket")
    private LocalDate dateBucket;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public ChatCache(String cacheKey, String intent, String message, LocalDate dateBucket,
                     Map<String, Object> payload, Instant expiresAt) {
        this.cacheKey = cacheKey;
        this.intent = intent;
        this.message = message;
        this.dateBucket = dateBucket;
        this.payload = payload;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
