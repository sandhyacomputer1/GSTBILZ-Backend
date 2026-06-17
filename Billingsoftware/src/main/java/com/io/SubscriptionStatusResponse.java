package com.io;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatusResponse {
    private String subscriptionType;   // TRIAL, MONTHLY, YEARLY, NONE
    private String subscriptionStatus; // ACTIVE, EXPIRED
    private String planName;
    private LocalDate startDate;
    private LocalDate expiryDate;
    private long remainingDays;
    private boolean isExpired;
    private boolean isTrial;
    private boolean isActive;
}
