package com.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.entity.UserEntity;
import com.io.NotificationResponse;
import com.repository.UserRepository;
import com.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @GetMapping
    public List<NotificationResponse> getMyNotifications() {
        String userId = getCurrentUserId();
        return notificationService.getForUser(userId);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount() {
        String userId = getCurrentUserId();
        return Map.of("count", notificationService.getUnreadCount(userId));
    }

    @PutMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }

    @PutMapping("/read-all")
    public Map<String, String> markAllRead() {
        String userId = getCurrentUserId();
        notificationService.markAllRead(userId);
        return Map.of("status", "ok");
    }

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .map(UserEntity::getUserId)
                .orElse(email);
    }
}
