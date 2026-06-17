package com.io;

import lombok.Data;

@Data
public class SubscriptionPlanRequest {
    private String planName;
    private String planType; // MONTHLY, YEARLY
    private Double price;
    private String description;
}
