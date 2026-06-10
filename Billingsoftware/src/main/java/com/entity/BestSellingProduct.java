package com.entity;
 
import jakarta.persistence.*;
import lombok.*;
 
@Entity
@Table(name = "tbl_best_selling_products")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BestSellingProduct {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
 
    private String productId;
 
    private String productName;
 
    private Long quantitySold;
 
    private Double totalRevenue;
    
    @Column(name = "user_id")
    private String userId;
}
