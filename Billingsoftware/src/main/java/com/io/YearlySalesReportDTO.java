package com.io;
 
import lombok.*;
 
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class YearlySalesReportDTO {
    private Integer year;
    private Double totalSales;
    private Long totalOrders;
    private Double totalTax;
    private Double totalDiscount;
}
