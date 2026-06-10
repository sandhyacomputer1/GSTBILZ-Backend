package com.entity;
 
import jakarta.persistence.*;
import lombok.*;
 
@Entity
@Table(name = "tbl_profit_loss_reports")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfitLossReport {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;
 
    private Double totalRevenue;
 
    private Double totalCost;
 
    private Double totalProfit;
 
    private Double totalLoss;
 
    private String reportPeriod; // e.g. "2026-06" or date range
    
    @Column(name = "user_id")
    private String userId;
}
