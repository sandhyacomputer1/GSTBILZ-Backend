package com.io;

import java.sql.Timestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private String userId;
    private String title;
    private String message;
    private String notificationType;
    private boolean isRead;
    private Timestamp createdAt;
}
