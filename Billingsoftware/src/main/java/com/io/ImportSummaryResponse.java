package com.io;

import lombok.*;
import java.util.List;

/**
 * Unified response for both preview and confirm import phases.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportSummaryResponse {

    // --- Stats ---
    private int totalRecords;
    private int successCount;
    private int failedCount;
    private int duplicatesFound;
    private long processingTimeMs;

    // --- Per-row statuses (used in summary/log after confirm) ---
    private List<ImportRowStatus> importedItems;

    // --- Preview products (used in preview step, before confirm) ---
    private List<ProductImportDTO> previewItems;
}
