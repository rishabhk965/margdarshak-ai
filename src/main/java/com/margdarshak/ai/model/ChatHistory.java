package com.margdarshak.ai.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "chat_history")
@Getter
@Setter
@NoArgsConstructor
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(length = 50)
    private String intent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> response;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ChatHistory(User user, String message, String intent, Map<String, Object> response) {
        this.user = user;
        this.message = message;
        this.intent = intent;
        this.response = response;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
