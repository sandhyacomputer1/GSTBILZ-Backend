package com.io;
 
import java.time.LocalDate;
import lombok.*;
 
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DailySalesReportDTO {
    private LocalDate date;
    private Double totalSales;
    private Long totalOrders;
    private Double totalTax;
    private Double totalDiscount;
}
