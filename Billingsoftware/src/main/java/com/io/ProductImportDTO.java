package com.io;

import lombok.*;
import java.math.BigDecimal;

/**
 * Full product DTO used during import preview and confirm phases.
 * Mapped from parsed file rows (Excel / CSV / PDF / DOCX).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImportDTO {

    private int rowNumber;

    private String productName;

    private String sku;

    private String barcode;

    private String category;

    private String brand;

    private BigDecimal purchasePrice;

    private BigDecimal sellingPrice;

    private Integer stockQuantity;

    private BigDecimal gstPercentage;

    private String unit;

    private String description;

    private String imageUrl;

    // --- Validation metadata (populated during preview) ---

    /** "VALID", "DUPLICATE_SKU", "DUPLICATE_BARCODE", "INVALID" */
    private String rowStatus;

    private String validationError;
}
