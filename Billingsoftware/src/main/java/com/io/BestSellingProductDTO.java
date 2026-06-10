package com.io;
 
import lombok.*;
 
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BestSellingProductDTO {
    private String productId;
    private String productName;
    private Long quantitySold;
    private Double totalRevenue;
}
