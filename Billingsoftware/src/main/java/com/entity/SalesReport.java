package com.entity;
 
import java.time.LocalDate;
import jakarta.persistence.*;
import lombok.*;
 
@Entity
@Table(name = "tbl_sales_reports")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SalesReport {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reportId;
 
    private String reportType; // DAILY, WEEKLY, MONTHLY, YEARLY, GST
 
    private Double totalSales;
 
    private Long totalOrders;
 
    private Double totalTax;
 
    private Double totalDiscount;
 
    private LocalDate generatedDate;
    
    @Column(name = "user_id")
    private String userId;
}
