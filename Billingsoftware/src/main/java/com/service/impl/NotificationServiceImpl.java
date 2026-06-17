package com.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.entity.NotificationEntity;
import com.io.NotificationResponse;
import com.repository.NotificationRepository;
import com.service.NotificationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public NotificationResponse create(String userId, String title, String message, String notificationType) {
        NotificationEntity entity = NotificationEntity.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .notificationType(notificationType)
                .isRead(false)
                .build();
        return toResponse(notificationRepository.save(entity));
    }

    @Override
    public List<NotificationResponse> getForUser(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsRead(userId, false);
    }

    @Override
    public NotificationResponse markRead(Long notificationId) {
        NotificationEntity entity = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        entity.setRead(true);
        return toResponse(notificationRepository.save(entity));
    }

    @Override
    public void markAllRead(String userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    private NotificationResponse toResponse(NotificationEntity e) {
        return NotificationResponse.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .title(e.getTitle())
                .message(e.getMessage())
                .notificationType(e.getNotificationType())
                .isRead(e.isRead())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
