package com.io;

import lombok.*;
import java.util.List;

/**
 * Import confirm request body — sent from the frontend after preview review.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportConfirmRequest {

    /**
     * Full list of parsed products from the preview step.
     */
    private List<ProductImportDTO> products;

    /**
     * How to handle duplicates (by SKU or barcode):
     *   SKIP   — skip duplicate rows entirely
     *   UPDATE — overwrite existing product fields
     *   CREATE — insert as a new product (generates new UUID, ignores existing)
     */
    private String duplicateMode; // SKIP | UPDATE | CREATE
}
