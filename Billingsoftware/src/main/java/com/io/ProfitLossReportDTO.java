package com.io;
 
import lombok.*;
 
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfitLossReportDTO {
    private String reportPeriod;
    private Double totalRevenue;
    private Double totalCost;
    private Double totalProfit;
    private Double totalLoss;
}
