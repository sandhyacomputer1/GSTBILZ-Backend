package com.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "import_history")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImportHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String importId;       // UUID

    private String importedBy;     // username / "admin"

    private String fileName;

    private String fileType;       // EXCEL, CSV, PDF, DOCX

    private int totalRecords;

    private int successCount;

    private int failedCount;

    private int duplicatesFound;

    private long processingTimeMs;

    @Column(name = "user_id")
    private String userId;

    @org.hibernate.annotations.CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
