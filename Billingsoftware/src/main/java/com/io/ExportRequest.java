package com.io;

import lombok.*;

/**
 * Filter parameters for product export.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportRequest {

    /** Export format: EXCEL, CSV, PDF */
    private String format;

    /** Filter by category name (optional, null = all) */
    private String category;

    /** Filter by brand (optional, null = all) */
    private String brand;

    /** If true, only export products with stockQuantity > 0 */
    private boolean inStockOnly;
}
