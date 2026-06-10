package com.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailService {

    @Autowired
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
}
