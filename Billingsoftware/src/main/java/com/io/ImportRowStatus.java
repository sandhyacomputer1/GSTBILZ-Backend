package com.io;

import lombok.*;
import java.math.BigDecimal;

/**
 * Per-row status record returned in the import summary after confirm phase.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportRowStatus {

    private int rowNumber;

    private String name;

    private String sku;

    private BigDecimal price;

    private Integer stockQuantity;

    /** "SUCCESS", "FAILED", "SKIPPED", "UPDATED" */
    private String status;

    /** Duplicate type if applicable: SKIP, UPDATE, CREATE */
    private String duplicateAction;

    private String errorMessage;
}
