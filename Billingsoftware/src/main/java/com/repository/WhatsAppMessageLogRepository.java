package com.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.entity.WhatsAppMessageLog;

@Repository
public interface WhatsAppMessageLogRepository extends JpaRepository<WhatsAppMessageLog, Long> {
    List<WhatsAppMessageLog> findByUserIdOrderBySentTimeDesc(String userId);

    @Query("SELECT COUNT(l) FROM WhatsAppMessageLog l WHERE l.userId = :userId")
    Long countTotalSentByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(l) FROM WhatsAppMessageLog l WHERE l.userId = :userId AND l.status = 'FAILED'")
    Long countFailedByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(l) FROM WhatsAppMessageLog l WHERE l.userId = :userId AND l.status = 'SENT'")
    Long countSuccessByUserId(@Param("userId") String userId);
}
