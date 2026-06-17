package com.service;

import java.util.List;

import com.io.NotificationResponse;

public interface NotificationService {
    NotificationResponse create(String userId, String title, String message, String notificationType);
    List<NotificationResponse> getForUser(String userId);
    long getUnreadCount(String userId);
    NotificationResponse markRead(Long notificationId);
    void markAllRead(String userId);
}
