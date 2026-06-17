package com.service.impl;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.entity.SubscriptionEntity;
import com.entity.SubscriptionPlanEntity;
import com.entity.UserEntity;
import com.io.SubscriptionPlanRequest;
import com.io.SubscriptionPlanResponse;
import com.io.SubscriptionRequest;
import com.io.SubscriptionResponse;
import com.io.SubscriptionStatsResponse;
import com.io.SubscriptionStatusResponse;
import com.repository.SubscriptionPlanRepository;
import com.repository.SubscriptionRepository;
import com.repository.UserRepository;
import com.repository.RenewalRequestRepository;
import com.service.SubscriptionService;
import com.service.NotificationService;
import com.service.EmailService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final RenewalRequestRepository renewalRequestRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    // ─── Plans ────────────────────────────────────────────────────────────────

    @Override
    public List<SubscriptionPlanResponse> getAllPlans() {
        return planRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toPlanResponse).collect(Collectors.toList());
    }

    @Override
    public SubscriptionPlanResponse createPlan(SubscriptionPlanRequest request) {
        SubscriptionPlanEntity plan = SubscriptionPlanEntity.builder()
                .planName(request.getPlanName())
                .planType(request.getPlanType())
                .price(request.getPrice())
                .description(request.getDescription())
                .status("ACTIVE")
                .build();
        return toPlanResponse(planRepository.save(plan));
    }

    @Override
    public SubscriptionPlanResponse updatePlan(Long id, SubscriptionPlanRequest request) {
        SubscriptionPlanEntity plan = planRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));
        plan.setPlanName(request.getPlanName());
        plan.setPlanType(request.getPlanType());
        plan.setPrice(request.getPrice());
        plan.setDescription(request.getDescription());
        return toPlanResponse(planRepository.save(plan));
    }

    @Override
    public void deletePlan(Long id) {
        if (!planRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found");
        }
        planRepository.deleteById(id);
    }

    @Override
    public SubscriptionPlanResponse togglePlanStatus(Long id) {
        SubscriptionPlanEntity plan = planRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));
        plan.setStatus("ACTIVE".equals(plan.getStatus()) ? "INACTIVE" : "ACTIVE");
        return toPlanResponse(planRepository.save(plan));
    }

    // ─── Subscriptions ────────────────────────────────────────────────────────

    @Override
    public List<SubscriptionResponse> getAllSubscriptions() {
        syncExpiredSubscriptions();
        return subscriptionRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(this::toSubscriptionResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SubscriptionResponse assignSubscription(SubscriptionRequest request) {
        UserEntity shopOwner = userRepository.findByUserId(request.getShopOwnerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop owner not found"));

        SubscriptionPlanEntity plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));

        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now();
        int durationDays = "MONTHLY".equals(plan.getPlanType()) ? 30 : 365;
        LocalDate expiryDate = startDate.plusDays(durationDays);

        SubscriptionEntity sub = SubscriptionEntity.builder()
                .shopOwnerId(shopOwner.getUserId())
                .shopOwnerName(shopOwner.getName())
                .shopOwnerEmail(shopOwner.getEmail())
                .shopName(shopOwner.getShopName())
                .planId(plan.getId())
                .planName(plan.getPlanName())
                .subscriptionType(plan.getPlanType())
                .planType(plan.getPlanType())
                .startDate(startDate)
                .expiryDate(expiryDate)
                .amountPaid(request.getAmountPaid() != null ? request.getAmountPaid() : plan.getPrice())
                .paymentStatus(request.getPaymentStatus() != null ? request.getPaymentStatus() : "PAID")
                .subscriptionStatus("ACTIVE")
                .build();

        SubscriptionResponse savedSub = toSubscriptionResponse(subscriptionRepository.save(sub));

        // Resolve any pending renewal requests
        try {
            renewalRequestRepository.findFirstByShopOwnerIdAndStatus(shopOwner.getUserId(), "PENDING")
                    .ifPresent(req -> {
                        req.setStatus("RESOLVED");
                        renewalRequestRepository.save(req);
                    });
        } catch (Exception e) {
            // non-critical
        }

        // Send in-app notification
        try {
            notificationService.create(
                shopOwner.getUserId(),
                "Subscription Activated",
                "Your subscription has been activated successfully. You can now continue using all billing features.",
                "INFO"
            );
        } catch (Exception e) {
            // ignore
        }

        // Send email notification
        try {
            emailService.sendSubscriptionActivatedEmail(shopOwner.getEmail(), shopOwner.getName());
        } catch (Exception e) {
            // ignore
        }

        return savedSub;
    }

    @Override
    public SubscriptionResponse cancelSubscription(Long id) {
        SubscriptionEntity sub = subscriptionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
        sub.setSubscriptionStatus("EXPIRED");
        return toSubscriptionResponse(subscriptionRepository.save(sub));
    }

    @Override
    public List<SubscriptionResponse> getExpiringSoon() {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysLater = today.plusDays(7);
        return subscriptionRepository.findExpiringSoon(today, sevenDaysLater)
                .stream().map(this::toSubscriptionResponse).collect(Collectors.toList());
    }

    // ─── Trial ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public SubscriptionResponse assignFreeTrial(String shopOwnerId) {
        UserEntity shopOwner = userRepository.findByUserId(shopOwnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop owner not found"));

        LocalDate today = LocalDate.now();
        SubscriptionEntity trial = SubscriptionEntity.builder()
                .shopOwnerId(shopOwner.getUserId())
                .shopOwnerName(shopOwner.getName())
                .shopOwnerEmail(shopOwner.getEmail())
                .shopName(shopOwner.getShopName())
                .planId(null)
                .planName("7-Day Free Trial")
                .subscriptionType("TRIAL")
                .planType("TRIAL")
                .startDate(today)
                .expiryDate(today.plusDays(7))
                .amountPaid(0.0)
                .paymentStatus("FREE")
                .subscriptionStatus("ACTIVE")
                .build();

        SubscriptionResponse savedSub = toSubscriptionResponse(subscriptionRepository.save(trial));

        // Send in-app notification
        try {
            notificationService.create(
                shopOwner.getUserId(),
                "Free Trial Activated",
                "Your 7-day free trial has started! Enjoy full access to all features.",
                "INFO"
            );
        } catch (Exception e) {
            // ignore
        }

        return savedSub;
    }

    // ─── Shop Owner Self-Service ───────────────────────────────────────────────

    @Override
    public SubscriptionStatusResponse getMySubscriptionStatus(String shopOwnerId) {
        syncExpiredSubscriptionsForOwner(shopOwnerId);

        // Prefer an ACTIVE subscription
        SubscriptionEntity active = subscriptionRepository
                .findTopByShopOwnerIdAndSubscriptionStatusOrderByCreatedAtDesc(shopOwnerId, "ACTIVE")
                .orElse(null);

        if (active != null) {
            long remaining = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), active.getExpiryDate()));
            return SubscriptionStatusResponse.builder()
                    .subscriptionType(active.getSubscriptionType())
                    .subscriptionStatus("ACTIVE")
                    .planName(active.getPlanName())
                    .startDate(active.getStartDate())
                    .expiryDate(active.getExpiryDate())
                    .remainingDays(remaining)
                    .isExpired(false)
                    .isTrial("TRIAL".equals(active.getSubscriptionType()))
                    .isActive(true)
                    .build();
        }

        // Fall back to most recent (likely EXPIRED)
        SubscriptionEntity latest = subscriptionRepository
                .findTopByShopOwnerIdOrderByCreatedAtDesc(shopOwnerId)
                .orElse(null);

        if (latest != null) {
            return SubscriptionStatusResponse.builder()
                    .subscriptionType(latest.getSubscriptionType())
                    .subscriptionStatus("EXPIRED")
                    .planName(latest.getPlanName())
                    .startDate(latest.getStartDate())
                    .expiryDate(latest.getExpiryDate())
                    .remainingDays(0)
                    .isExpired(true)
                    .isTrial("TRIAL".equals(latest.getSubscriptionType()))
                    .isActive(false)
                    .build();
        }

        // No subscription at all
        return SubscriptionStatusResponse.builder()
                .subscriptionType("NONE")
                .subscriptionStatus("EXPIRED")
                .remainingDays(0)
                .isExpired(true)
                .isTrial(false)
                .isActive(false)
                .build();
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    @Override
    public SubscriptionStatsResponse getStats() {
        syncExpiredSubscriptions();
        List<SubscriptionResponse> expiringSoon = getExpiringSoon();

        return SubscriptionStatsResponse.builder()
                .totalPlans(planRepository.count())
                .activeSubscriptions(subscriptionRepository.countBySubscriptionStatus("ACTIVE"))
                .expiredSubscriptions(subscriptionRepository.countBySubscriptionStatus("EXPIRED"))
                .pendingSubscriptions(subscriptionRepository.countBySubscriptionStatus("PENDING"))
                .trialAccounts(subscriptionRepository.countBySubscriptionType("TRIAL"))
                .expiringWithin7Days((long) expiringSoon.size())
                .monthlyRevenue(subscriptionRepository.sumRevenueByPlanType("MONTHLY"))
                .yearlyRevenue(subscriptionRepository.sumRevenueByPlanType("YEARLY"))
                .totalRevenue(subscriptionRepository.sumTotalRevenue())
                .expiringSoonList(expiringSoon)
                .build();
    }

    // ─── Sync ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void syncExpiredSubscriptions() {
        subscriptionRepository.findExpiredButStillActive(LocalDate.now())
                .forEach(s -> {
                    s.setSubscriptionStatus("EXPIRED");
                    subscriptionRepository.save(s);
                });
    }

    private void syncExpiredSubscriptionsForOwner(String shopOwnerId) {
        subscriptionRepository.findByShopOwnerId(shopOwnerId).stream()
                .filter(s -> "ACTIVE".equals(s.getSubscriptionStatus())
                        && s.getExpiryDate().isBefore(LocalDate.now()))
                .forEach(s -> {
                    s.setSubscriptionStatus("EXPIRED");
                    subscriptionRepository.save(s);
                });
    }

    // ─── Converters ───────────────────────────────────────────────────────────

    private SubscriptionPlanResponse toPlanResponse(SubscriptionPlanEntity plan) {
        int durationDays = "MONTHLY".equals(plan.getPlanType()) ? 30 : 365;
        return SubscriptionPlanResponse.builder()
                .id(plan.getId())
                .planName(plan.getPlanName())
                .planType(plan.getPlanType())
                .price(plan.getPrice())
                .description(plan.getDescription())
                .status(plan.getStatus())
                .durationDays(durationDays)
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    private SubscriptionResponse toSubscriptionResponse(SubscriptionEntity sub) {
        long remaining = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), sub.getExpiryDate()));
        return SubscriptionResponse.builder()
                .id(sub.getId())
                .shopOwnerId(sub.getShopOwnerId())
                .shopOwnerName(sub.getShopOwnerName())
                .shopOwnerEmail(sub.getShopOwnerEmail())
                .shopName(sub.getShopName())
                .planId(sub.getPlanId())
                .planName(sub.getPlanName())
                .planType(sub.getSubscriptionType())
                .startDate(sub.getStartDate())
                .expiryDate(sub.getExpiryDate())
                .amountPaid(sub.getAmountPaid())
                .paymentStatus(sub.getPaymentStatus())
                .subscriptionStatus(sub.getSubscriptionStatus())
                .remainingDays(remaining)
                .createdAt(sub.getCreatedAt())
                .updatedAt(sub.getUpdatedAt())
                .build();
    }
}
