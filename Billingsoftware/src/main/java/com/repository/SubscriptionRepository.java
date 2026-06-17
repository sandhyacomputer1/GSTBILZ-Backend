package com.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.entity.SubscriptionEntity;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {

    List<SubscriptionEntity> findBySubscriptionStatus(String status);

    List<SubscriptionEntity> findByShopOwnerId(String shopOwnerId);

    List<SubscriptionEntity> findAllByOrderByCreatedAtDesc();

    Long countBySubscriptionStatus(String status);

    Long countBySubscriptionType(String subscriptionType);

    // Most recent subscription for a shop owner (for status check)
    Optional<SubscriptionEntity> findTopByShopOwnerIdOrderByCreatedAtDesc(String shopOwnerId);

    // Active subscription for a shop owner
    Optional<SubscriptionEntity> findTopByShopOwnerIdAndSubscriptionStatusOrderByCreatedAtDesc(
            String shopOwnerId, String subscriptionStatus);

    // Subscriptions expiring within a date range that haven't been notified yet
    @Query("SELECT s FROM SubscriptionEntity s WHERE s.expiryDate BETWEEN :today AND :sevenDaysLater AND s.subscriptionStatus = 'ACTIVE'")
    List<SubscriptionEntity> findExpiringSoon(
            @Param("today") LocalDate today,
            @Param("sevenDaysLater") LocalDate sevenDaysLater);

    // Find subscriptions expiring on a specific date (for day-level notifications)
    @Query("SELECT s FROM SubscriptionEntity s WHERE s.expiryDate = :date AND s.subscriptionStatus = 'ACTIVE'")
    List<SubscriptionEntity> findExpiringOnDate(@Param("date") LocalDate date);

    // Find active subscriptions already past expiry (needs status sync)
    @Query("SELECT s FROM SubscriptionEntity s WHERE s.expiryDate < :today AND s.subscriptionStatus = 'ACTIVE'")
    List<SubscriptionEntity> findExpiredButStillActive(@Param("today") LocalDate today);

    @Query("SELECT COALESCE(SUM(s.amountPaid), 0) FROM SubscriptionEntity s WHERE s.subscriptionType = :type AND s.paymentStatus = 'PAID'")
    Double sumRevenueByPlanType(@Param("type") String type);

    @Query("SELECT COALESCE(SUM(s.amountPaid), 0) FROM SubscriptionEntity s WHERE s.paymentStatus = 'PAID'")
    Double sumTotalRevenue();
}
