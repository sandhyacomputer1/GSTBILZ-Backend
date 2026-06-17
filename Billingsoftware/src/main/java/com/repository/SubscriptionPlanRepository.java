package com.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.entity.SubscriptionPlanEntity;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlanEntity, Long> {
    List<SubscriptionPlanEntity> findByStatus(String status);
    List<SubscriptionPlanEntity> findByPlanType(String planType);
    List<SubscriptionPlanEntity> findAllByOrderByCreatedAtDesc();
}
