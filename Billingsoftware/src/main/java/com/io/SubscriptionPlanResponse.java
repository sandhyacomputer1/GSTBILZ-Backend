package com.io;

import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanResponse {
    private Long id;
    private String planName;
    private String planType;
    private Double price;
    private String description;
    private String status;
    private int durationDays; // 30 for MONTHLY, 365 for YEARLY
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
