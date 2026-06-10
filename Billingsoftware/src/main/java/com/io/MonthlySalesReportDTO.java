package com.io;
 
import lombok.*;
 
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonthlySalesReportDTO {
    private String month; // e.g., "2026-06" or "June"
    private Double totalSales;
    private Long totalOrders;
    private Double totalTax;
    private Double totalDiscount;
}
