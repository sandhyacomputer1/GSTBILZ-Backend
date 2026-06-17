package com.entity;

import java.sql.Timestamp;
import java.time.LocalDate;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String shopOwnerId;

    private String shopOwnerName;
    private String shopOwnerEmail;
    private String shopName;

    // null for TRIAL subscriptions
    private Long planId;

    private String planName;

    // TRIAL, MONTHLY, YEARLY
    @Column(nullable = false)
    @Builder.Default
    private String subscriptionType = "MONTHLY";

    // kept for backward compatibility (mirrors subscriptionType for paid plans)
    private String planType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate expiryDate;

    private Double amountPaid;

    @Column(nullable = false)
    @Builder.Default
    private String paymentStatus = "PAID"; // PAID, PENDING, FAILED, FREE

    // ACTIVE, EXPIRED, PENDING
    @Column(nullable = false)
    @Builder.Default
    private String subscriptionStatus = "ACTIVE";

    // Whether expiry-warning notifications have been sent (prevents duplicate sends)
    @Builder.Default
    private boolean notified7Days = false;
    @Builder.Default
    private boolean notified3Days = false;
    @Builder.Default
    private boolean notified1Day  = false;
    @Builder.Default
    private boolean notifiedExpired = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    private Timestamp updatedAt;
}
