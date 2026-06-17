package com.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.entity.UserEntity;
import com.io.SubscriptionPlanRequest;
import com.io.SubscriptionPlanResponse;
import com.io.SubscriptionRequest;
import com.io.SubscriptionResponse;
import com.io.SubscriptionStatsResponse;
import com.io.SubscriptionStatusResponse;
import com.repository.UserRepository;
import com.repository.RenewalRequestRepository;
import com.service.SubscriptionService;
import com.service.NotificationService;
import com.service.EmailService;
import com.entity.RenewalRequestEntity;
import java.util.stream.Collectors;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final RenewalRequestRepository renewalRequestRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    // ─── Plans (SuperAdmin only) ──────────────────────────────────────────────

    @GetMapping("/admin/subscriptions/plans")
    public List<SubscriptionPlanResponse> getAllPlans() {
        return subscriptionService.getAllPlans();
    }

    @PostMapping("/admin/subscriptions/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionPlanResponse createPlan(@RequestBody SubscriptionPlanRequest request) {
        return subscriptionService.createPlan(request);
    }

    @PutMapping("/admin/subscriptions/plans/{id}")
    public SubscriptionPlanResponse updatePlan(@PathVariable Long id, @RequestBody SubscriptionPlanRequest request) {
        return subscriptionService.updatePlan(id, request);
    }

    @DeleteMapping("/admin/subscriptions/plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable Long id) {
        subscriptionService.deletePlan(id);
    }

    @PutMapping("/admin/subscriptions/plans/{id}/toggle-status")
    public SubscriptionPlanResponse togglePlanStatus(@PathVariable Long id) {
        return subscriptionService.togglePlanStatus(id);
    }

    // ─── Subscriptions (SuperAdmin only) ─────────────────────────────────────

    @GetMapping("/admin/subscriptions")
    public List<SubscriptionResponse> getAllSubscriptions() {
        return subscriptionService.getAllSubscriptions();
    }

    @PostMapping("/admin/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionResponse assignSubscription(@RequestBody SubscriptionRequest request) {
        return subscriptionService.assignSubscription(request);
    }

    @PutMapping("/admin/subscriptions/{id}/cancel")
    public SubscriptionResponse cancelSubscription(@PathVariable Long id) {
        return subscriptionService.cancelSubscription(id);
    }

    @GetMapping("/admin/subscriptions/expiring-soon")
    public List<SubscriptionResponse> getExpiringSoon() {
        return subscriptionService.getExpiringSoon();
    }

    @GetMapping("/admin/subscriptions/stats")
    public SubscriptionStatsResponse getStats() {
        return subscriptionService.getStats();
    }

    // ─── Shop Owner self-service ──────────────────────────────────────────────

    @GetMapping("/subscription-status")
    public SubscriptionStatusResponse getMyStatus() {
        String shopOwnerId = getCurrentUserId();
        return subscriptionService.getMySubscriptionStatus(shopOwnerId);
    }

    // ─── Public: Super Admin contact info ────────────────────────────────────

    @GetMapping("/public/admin-contact")
    public Map<String, String> getAdminContact() {
        return userRepository.findAll().stream()
                .filter(u -> "ROLE_SUPERADMIN".equals(u.getRole()))
                .findFirst()
                .map(u -> Map.of(
                        "name",  u.getName()  != null ? u.getName()  : "Super Admin",
                        "email", u.getEmail() != null ? u.getEmail() : ""
                ))
                .orElse(Map.of("name", "Super Admin", "email", ""));
    }

    // ─── Renewal Requests ───────────────────────────────────────────────────

    @PostMapping("/subscription-renewal-request")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> requestSubscriptionRenewal() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        String shopOwnerId = "ROLE_EMPLOYEE".equals(user.getRole()) ? user.getShopOwnerId() : user.getUserId();
        UserEntity shopOwner = user;
        if ("ROLE_EMPLOYEE".equals(user.getRole()) && shopOwnerId != null) {
            shopOwner = userRepository.findByUserId(shopOwnerId)
                    .orElse(user);
        }

        SubscriptionStatusResponse status = subscriptionService.getMySubscriptionStatus(shopOwnerId);

        // Check if there is already a PENDING request for this user
        boolean exists = renewalRequestRepository.findFirstByShopOwnerIdAndStatus(shopOwnerId, "PENDING").isPresent();
        if (exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A renewal request is already pending.");
        }

        // Save the renewal request in DB
        RenewalRequestEntity request = RenewalRequestEntity.builder()
                .shopOwnerId(shopOwnerId)
                .shopName(shopOwner.getShopName() != null ? shopOwner.getShopName() : "Shop Name")
                .ownerName(shopOwner.getName() != null ? shopOwner.getName() : "Owner Name")
                .email(shopOwner.getEmail())
                .mobile(shopOwner.getShopMobile() != null ? shopOwner.getShopMobile() : "")
                .subscriptionStatus(status.getSubscriptionStatus())
                .expiryDate(status.getExpiryDate())
                .status("PENDING")
                .build();
        
        renewalRequestRepository.save(request);

        // Find Super Admin and send email & create notification
        List<UserEntity> superAdmins = userRepository.findAll().stream()
                .filter(u -> "ROLE_SUPERADMIN".equals(u.getRole()))
                .collect(Collectors.toList());

        String expiryDateStr = status.getExpiryDate() != null ? status.getExpiryDate().toString() : "N/A";
        
        for (UserEntity admin : superAdmins) {
            // Send email
            if (admin.getEmail() != null && !admin.getEmail().isEmpty()) {
                emailService.sendSubscriptionRenewalRequestEmail(
                        admin.getEmail(),
                        request.getShopName(),
                        request.getOwnerName(),
                        request.getEmail(),
                        request.getMobile(),
                        request.getSubscriptionStatus(),
                        expiryDateStr
                );
            }

            // Send in-app notification
            String msg = "Shop Name: " + request.getShopName() + "\n"
                    + "Owner: " + request.getOwnerName() + "\n"
                    + "Subscription Expired/Expiring\n"
                    + "Request Time: " + java.time.LocalDateTime.now().toString();
            notificationService.create(admin.getUserId(), "Renewal Request Received", msg, "RENEWAL");
        }

        return Map.of("message", "Renewal request submitted successfully.");
    }


    @GetMapping("/admin/renewal-requests")
    public List<RenewalRequestEntity> getPendingRenewalRequests() {
        return renewalRequestRepository.findByStatusOrderByRequestDateDesc("PENDING");
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .map(u -> "ROLE_EMPLOYEE".equals(u.getRole()) ? u.getShopOwnerId() : u.getUserId())
                .orElse(email);
    }
}
