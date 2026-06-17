package com.io;

import java.math.BigDecimal;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemRequest {

    private String name;
    private BigDecimal price;
    private String categoryId;
    private String description;
    private BigDecimal gstPercentage;
    private Integer stockQuantity;
}