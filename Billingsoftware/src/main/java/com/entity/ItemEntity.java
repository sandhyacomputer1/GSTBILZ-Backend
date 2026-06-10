package com.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_items")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ItemEntity {

    @Id
    private String itemId;   // ✅ UUID STRING PRIMARY KEY

    private String name;

    private String sku;      // Unique stock-keeping unit

    private String barcode;  // Unique barcode

    private String brand;

    private String unit;     // e.g. PCS, KG, LTR

    private BigDecimal price; // Backward-compat alias for sellingPrice

    private BigDecimal purchasePrice;

    private BigDecimal sellingPrice;

    private Integer stockQuantity;

    private BigDecimal gstPercentage;

    private String description;

    private String imgUrl;

    @Column(name = "user_id")
    private String userId;

    @org.hibernate.annotations.CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @org.hibernate.annotations.UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private CategoryEntity category;
}