package com.service;

import java.util.List;

import com.io.SubscriptionPlanRequest;
import com.io.SubscriptionPlanResponse;
import com.io.SubscriptionRequest;
import com.io.SubscriptionResponse;
import com.io.SubscriptionStatsResponse;
import com.io.SubscriptionStatusResponse;

public interface SubscriptionService {

    // Plans
    List<SubscriptionPlanResponse> getAllPlans();
    SubscriptionPlanResponse createPlan(SubscriptionPlanRequest request);
    SubscriptionPlanResponse updatePlan(Long id, SubscriptionPlanRequest request);
    void deletePlan(Long id);
    SubscriptionPlanResponse togglePlanStatus(Long id);

    // Subscriptions (admin)
    List<SubscriptionResponse> getAllSubscriptions();
    SubscriptionResponse assignSubscription(SubscriptionRequest request);
    SubscriptionResponse cancelSubscription(Long id);
    List<SubscriptionResponse> getExpiringSoon();

    // Trial
    SubscriptionResponse assignFreeTrial(String shopOwnerId);

    // Shop owner — self-service status query
    SubscriptionStatusResponse getMySubscriptionStatus(String shopOwnerId);

    // Stats
    SubscriptionStatsResponse getStats();

    // Sync expired statuses
    void syncExpiredSubscriptions();
}
