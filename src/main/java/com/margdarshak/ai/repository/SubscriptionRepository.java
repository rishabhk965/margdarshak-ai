package com.margdarshak.ai.repository;

import com.margdarshak.ai.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByUserIdAndActiveTrue(Long userId);

    List<Subscription> findByAlertTypeAndActiveTrue(String alertType);
}
