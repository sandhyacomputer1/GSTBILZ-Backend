package com.io;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ItemResponse {

    private String itemId;
    private String name;
    private BigDecimal price;
    private String description;
    private String categoryId;
    private String categoryName;
    private String imgUrl;
    private BigDecimal gstPercentage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}