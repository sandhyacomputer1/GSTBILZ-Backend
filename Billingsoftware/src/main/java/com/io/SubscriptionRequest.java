package com.io;

import java.time.LocalDate;

import lombok.Data;

@Data
public class SubscriptionRequest {
    private String shopOwnerId;
    private Long planId;
    private LocalDate startDate;
    private Double amountPaid;
    private String paymentStatus; // PAID, PENDING, FAILED
}
