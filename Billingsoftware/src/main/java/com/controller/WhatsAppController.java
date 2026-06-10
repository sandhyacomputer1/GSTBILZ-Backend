package com.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.entity.UserEntity;
import com.entity.WhatsAppMessageLog;
import com.repository.UserRepository;
import com.repository.WhatsAppMessageLogRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
public class WhatsAppController {

    private final WhatsAppMessageLogRepository messageLogRepository;
    private final UserRepository userRepository;

    private UserEntity getLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @GetMapping("/logs")
    public List<WhatsAppMessageLog> getLogs() {
        UserEntity currentUser = getLoggedInUser();
        return messageLogRepository.findByUserIdOrderBySentTimeDesc(currentUser.getTenantId());
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        UserEntity currentUser = getLoggedInUser();
        String tenantId = currentUser.getTenantId();

        long total = messageLogRepository.countTotalSentByUserId(tenantId);
        long failed = messageLogRepository.countFailedByUserId(tenantId);
        long success = messageLogRepository.countSuccessByUserId(tenantId);
        double successRate = total == 0 ? 0.0 : ((double) success / total) * 100.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSent", total);
        stats.put("failedMessages", failed);
        stats.put("successRate", Double.isNaN(successRate) ? 0.0 : Math.round(successRate * 10.0) / 10.0);

        return stats;
    }
}
