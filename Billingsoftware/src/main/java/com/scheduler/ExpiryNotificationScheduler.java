package com.scheduler;

import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.entity.SubscriptionEntity;
import com.entity.UserEntity;
import com.repository.SubscriptionRepository;
import com.repository.UserRepository;
import com.service.EmailService;
import com.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryNotificationScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // Runs every day at 9:00 AM
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void runDailyExpiryCheck() {
        log.info("Running daily subscription expiry check...");
        syncExpired();
        sendExpiryWarnings();
        notifySuperAdminOfExpiries();
    }

    // Mark ACTIVE subscriptions whose expiry date has passed as EXPIRED
    private void syncExpired() {
        List<SubscriptionEntity> stale = subscriptionRepository.findExpiredButStillActive(LocalDate.now());
        for (SubscriptionEntity sub : stale) {
            sub.setSubscriptionStatus("EXPIRED");
            if (!sub.isNotifiedExpired()) {
                sub.setNotifiedExpired(true);
                sendExpiredNotifications(sub);
            }
            subscriptionRepository.save(sub);
        }
        if (!stale.isEmpty()) log.info("Marked {} subscription(s) as EXPIRED", stale.size());
    }

    // Send warnings for subscriptions expiring in 7, 3, or 1 day(s)
    private void sendExpiryWarnings() {
        LocalDate today = LocalDate.now();
        int[] warningDays = {7, 3, 1};

        for (int days : warningDays) {
            LocalDate targetDate = today.plusDays(days);
            List<SubscriptionEntity> expiring = subscriptionRepository.findExpiringOnDate(targetDate);

            for (SubscriptionEntity sub : expiring) {
                boolean alreadySent = switch (days) {
                    case 7 -> sub.isNotified7Days();
                    case 3 -> sub.isNotified3Days();
                    case 1 -> sub.isNotified1Day();
                    default -> false;
                };

                if (!alreadySent) {
                    sendWarningNotifications(sub, days);
                    switch (days) {
                        case 7 -> sub.setNotified7Days(true);
                        case 3 -> sub.setNotified3Days(true);
                        case 1 -> sub.setNotified1Day(true);
                    }
                    subscriptionRepository.save(sub);
                    log.info("Sent {}-day expiry warning for shop '{}'", days, sub.getShopName());
                }
            }
        }
    }

    private void sendWarningNotifications(SubscriptionEntity sub, int daysLeft) {
        String dayWord = daysLeft == 1 ? "tomorrow" : "in " + daysLeft + " days";
        String title = "Subscription Expiring " + (daysLeft == 1 ? "Tomorrow" : "in " + daysLeft + " Days");
        String message = "Your " + sub.getPlanName() + " will expire " + dayWord
                + " on " + sub.getExpiryDate() + ". Contact Super Admin to renew.";

        // In-app notification for shop owner
        notificationService.create(sub.getShopOwnerId(), title, message,
                "TRIAL".equals(sub.getSubscriptionType()) ? "TRIAL_EXPIRING" : "SUB_EXPIRING");

        // Email to shop owner
        emailService.sendExpiryWarningEmail(
                sub.getShopOwnerEmail(),
                sub.getShopOwnerName(),
                daysLeft,
                sub.getPlanName(),
                sub.getExpiryDate().toString());
    }

    private void sendExpiredNotifications(SubscriptionEntity sub) {
        String title = "Subscription Expired";
        String message = "Your " + sub.getPlanName() + " has expired. Billing operations are disabled. "
                + "Please contact Super Admin to renew your subscription.";

        // In-app notification for shop owner
        notificationService.create(sub.getShopOwnerId(), title, message,
                "TRIAL".equals(sub.getSubscriptionType()) ? "TRIAL_EXPIRED" : "SUB_EXPIRED");

        // Email to shop owner
        emailService.sendExpiredEmail(sub.getShopOwnerEmail(), sub.getShopOwnerName(), sub.getPlanName());
    }

    private void notifySuperAdminOfExpiries() {
        List<UserEntity> admins = userRepository.findAll().stream()
                .filter(u -> "ROLE_SUPERADMIN".equals(u.getRole()))
                .toList();

        if (admins.isEmpty()) return;

        LocalDate today = LocalDate.now();

        for (int days : new int[]{7, 3, 1, 0}) {
            List<SubscriptionEntity> subs = days == 0
                    ? subscriptionRepository.findExpiredButStillActive(today)
                    : subscriptionRepository.findExpiringOnDate(today.plusDays(days));

            for (SubscriptionEntity sub : subs) {
                String shopName = sub.getShopName() != null ? sub.getShopName() : sub.getShopOwnerEmail();
                String title = days == 0
                        ? "Shop Subscription Expired: " + shopName
                        : "Shop Subscription Expiring in " + days + " Day(s): " + shopName;
                String msg = days == 0
                        ? "Shop '" + shopName + "' — " + sub.getPlanName() + " has expired."
                        : "Shop '" + shopName + "' — " + sub.getPlanName() + " expires in " + days + " day(s) on " + sub.getExpiryDate() + ".";

                for (UserEntity admin : admins) {
                    notificationService.create(admin.getUserId(), title, msg,
                            days == 0 ? "SUB_EXPIRED" : "SUB_EXPIRING");
                    emailService.sendSuperAdminExpiryAlert(admin.getEmail(), shopName, sub.getPlanName(), days);
                }
            }
        }
    }
}
