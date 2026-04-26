package com.margdarshak.ai.repository;

import com.margdarshak.ai.model.ChatHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    Page<ChatHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
