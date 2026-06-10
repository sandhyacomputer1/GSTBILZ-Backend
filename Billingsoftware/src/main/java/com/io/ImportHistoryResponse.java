package com.io;

import lombok.*;
import java.time.LocalDateTime;

/**
 * Response DTO for a single import history record.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportHistoryResponse {

    private Long id;

    private String importId;

    private String importedBy;

    private String fileName;

    private String fileType;

    private int totalRecords;

    private int successCount;

    private int failedCount;

    private int duplicatesFound;

    private long processingTimeMs;

    private LocalDateTime createdAt;
}
