package com.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendVerificationEmail(String toEmail, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Verify Your Email Address - Billing Software");
            
            String verificationUrl = "http://localhost:5173/verify-email?token=" + token;
            message.setText("Welcome!\n\nPlease verify your email by clicking the link below:\n" + verificationUrl);
            
            mailSender.send(message);
            log.info("Verification email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
            // Since this is a test environment, we might not have real SMTP configured.
            // Logging the token so the user can verify manually from logs if needed.
            log.warn("MANUAL VERIFICATION LINK: http://localhost:5173/verify-email?token={}", token);
        }
    }

    public void sendRegistrationCompletedEmail(String toEmail, String shopName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Shop Registration Received - Under Review");
            message.setText("Hello,\n\nYour registration request for '" + shopName + "' has been successfully received.\n" +
                    "It is currently under review by our Super Admin. You will receive an email once it is approved.\n\n" +
                    "Thank you,\nBilling Software Team");
            mailSender.send(message);
            log.info("Registration completed email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send registration completed email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendAccountApprovedEmail(String toEmail, String ownerName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Shop Account Approved - Billing Software");
            message.setText("Hello " + ownerName + ",\n\nCongratulations! Your shop owner account has been approved by the Super Admin.\n" +
                    "You can now login and start managing your billing software.\n\n" +
                    "Login here: http://localhost:5173/login\n\n" +
                    "Best regards,\nBilling Software Team");
            mailSender.send(message);
            log.info("Account approved email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send account approved email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendAccountRejectedEmail(String toEmail, String ownerName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Shop Account Registration Rejected");
            message.setText("Hello " + ownerName + ",\n\nWe regret to inform you that your registration request has been rejected by the Super Admin.\n" +
                    "If you believe this was a mistake, please contact support.\n\n" +
                    "Best regards,\nBilling Software Team");
            mailSender.send(message);
            log.info("Account rejected email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send account rejected email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendTrialStartEmail(String toEmail, String ownerName, String expiryDate) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Your 7-Day Free Trial Has Started - Billing Software");
            message.setText("Hello " + ownerName + ",\n\n"
                    + "Your 7-day free trial has started! You now have full access to all features of the Billing Software.\n\n"
                    + "Trial expires on: " + expiryDate + "\n\n"
                    + "To continue using the software after your trial, please contact the Super Admin to assign a subscription plan.\n\n"
                    + "Login here: http://localhost:5173/login\n\n"
                    + "Best regards,\nBilling Software Team");
            mailSender.send(message);
            log.info("Trial start email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send trial start email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendExpiryWarningEmail(String toEmail, String ownerName, int daysLeft, String planName, String expiryDate) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            String dayWord = daysLeft == 1 ? "tomorrow" : "in " + daysLeft + " days";
            message.setSubject("Action Required: Your " + planName + " expires " + dayWord + " - Billing Software");
            message.setText("Hello " + ownerName + ",\n\n"
                    + "This is a reminder that your " + planName + " will expire " + dayWord + " (on " + expiryDate + ").\n\n"
                    + "After expiry, billing operations will be disabled until a new subscription is assigned.\n\n"
                    + "Please contact the Super Admin to renew your subscription.\n\n"
                    + "Login here: http://localhost:5173/login\n\n"
                    + "Best regards,\nBilling Software Team");
            mailSender.send(message);
            log.info("Expiry warning email ({} days) sent to {}", daysLeft, toEmail);
        } catch (Exception e) {
            log.error("Failed to send expiry warning email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendExpiredEmail(String toEmail, String ownerName, String planName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Your " + planName + " Has Expired - Billing Software");
            message.setText("Hello " + ownerName + ",\n\n"
                    + "Your " + planName + " has expired. Billing operations are currently disabled.\n\n"
                    + "Please contact the Super Admin to renew your subscription and regain full access.\n\n"
                    + "Login here: http://localhost:5173/login\n\n"
                    + "Best regards,\nBilling Software Team");
            mailSender.send(message);
            log.info("Expired email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send expired email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendSuperAdminExpiryAlert(String adminEmail, String shopName, String planName, int daysLeft) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(adminEmail);
            String subject = daysLeft == 0
                    ? "[Alert] " + shopName + " subscription expired"
                    : "[Alert] " + shopName + " subscription expires in " + daysLeft + " day(s)";
            message.setSubject(subject);
            String body = daysLeft == 0
                    ? "Shop '" + shopName + "' - " + planName + " has expired. Please assign a new subscription."
                    : "Shop '" + shopName + "' - " + planName + " will expire in " + daysLeft + " day(s). Consider renewing.";
            message.setText(body + "\n\nManage subscriptions: http://localhost:5173/subscription-management");
            mailSender.send(message);
            log.info("Super admin expiry alert sent for shop '{}' ({} days)", shopName, daysLeft);
        } catch (Exception e) {
            log.error("Failed to send super admin expiry alert: {}", e.getMessage());
        }
    }

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Password Reset OTP - Billing Software");
            message.setText("Hello,\n\nYour one-time password (OTP) to reset your password is:\n\n" +
                    otp + "\n\nThis OTP is valid for 5 minutes. Please do not share it with anyone.\n\n" +
                    "Best regards,\nBilling Software Team");
            mailSender.send(message);
            log.info("OTP email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendSubscriptionRenewalRequestEmail(String adminEmail, String shopName, String ownerName, String ownerEmail, String ownerMobile, String status, String expiryDate) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(adminEmail);
            message.setSubject("Subscription Renewal Request");
            message.setText("Hello Super Admin,\n\n"
                    + "The following shop owner has requested subscription renewal.\n\n"
                    + "Shop Name: " + shopName + "\n"
                    + "Owner Name: " + ownerName + "\n"
                    + "Email: " + ownerEmail + "\n"
                    + "Mobile: " + ownerMobile + "\n"
                    + "Subscription Status: " + status + "\n"
                    + "Expiry Date: " + expiryDate + "\n\n"
                    + "Please contact the shop owner for subscription activation.\n\n"
                    + "Thank You.");
            mailSender.send(message);
            log.info("Subscription renewal request email sent successfully to Super Admin {}", adminEmail);
        } catch (Exception e) {
            log.error("Failed to send subscription renewal request email: {}", e.getMessage());
        }
    }

    public void sendSubscriptionActivatedEmail(String toEmail, String ownerName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Subscription Activated - Billing Software");
            message.setText("Hello " + ownerName + ",\n\n"
                    + "Your subscription has been activated successfully. You can now continue using all billing features.\n\n"
                    + "Best regards,\nBilling Software Team");
            mailSender.send(message);
            log.info("Subscription activation email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send subscription activation email to {}: {}", toEmail, e.getMessage());
        }
    }
}
