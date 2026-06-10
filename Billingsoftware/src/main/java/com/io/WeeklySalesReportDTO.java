package com.io;
 
import lombok.*;
 
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WeeklySalesReportDTO {
    private String weekRange; // e.g. "Week 22 (May 25 - May 31)" or week number/range
    private Double totalSales;
    private Long totalOrders;
    private Double totalTax;
    private Double totalDiscount;
}
