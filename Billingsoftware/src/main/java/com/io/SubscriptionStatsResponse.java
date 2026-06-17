package com.io;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionStatsResponse {
    private long totalPlans;
    private long activeSubscriptions;
    private long expiredSubscriptions;
    private long pendingSubscriptions;
    private long trialAccounts;
    private long expiringWithin7Days;
    private Double monthlyRevenue;
    private Double yearlyRevenue;
    private Double totalRevenue;
    private List<SubscriptionResponse> expiringSoonList;
}
