package com.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.entity.NotificationEntity;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    Long countByUserIdAndIsRead(String userId, boolean isRead);

    @Modifying
    @Transactional
    @Query("UPDATE NotificationEntity n SET n.isRead = true WHERE n.userId = :userId")
    void markAllReadByUserId(@Param("userId") String userId);

    boolean existsByUserIdAndNotificationTypeAndIsRead(String userId, String notificationType, boolean isRead);
}
