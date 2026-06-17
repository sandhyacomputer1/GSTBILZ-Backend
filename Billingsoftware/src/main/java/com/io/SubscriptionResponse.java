package com.io;

import java.sql.Timestamp;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {
    private Long id;
    private String shopOwnerId;
    private String shopOwnerName;
    private String shopOwnerEmail;
    private String shopName;
    private Long planId;
    private String planName;
    private String planType;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private Double amountPaid;
    private String paymentStatus;
    private String subscriptionStatus;
    private long remainingDays;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
