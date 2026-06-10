package com.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.entity.ItemEntity;

public interface ItemRepository extends JpaRepository<ItemEntity, String> {

    Optional<ItemEntity> findByItemId(String itemId);
    Optional<ItemEntity> findByItemIdAndUserId(String itemId, String userId);

    Integer countByCategory_Id(Long id);  // ✅ FIXED (IMPORTANT)
    Integer countByCategory_IdAndUserId(Long id, String userId);

    List<ItemEntity> findByUserId(String userId);

    // --- Import duplicate detection ---
    Optional<ItemEntity> findBySku(String sku);
    List<ItemEntity> findBySkuAndUserId(String sku, String userId);

    Optional<ItemEntity> findByBarcode(String barcode);
    List<ItemEntity> findByBarcodeAndUserId(String barcode, String userId);

    // --- Export filters ---
    @Query("SELECT i FROM ItemEntity i LEFT JOIN i.category c WHERE " +
           "(:userId IS NULL OR i.userId = :userId) AND " +
           "(:category IS NULL OR c.name = :category) AND " +
           "(:brand IS NULL OR i.brand = :brand) AND " +
           "(:inStockOnly = false OR i.stockQuantity > 0)")
    List<ItemEntity> findByFilters(
        @Param("userId") String userId,
        @Param("category") String category,
        @Param("brand") String brand,
        @Param("inStockOnly") boolean inStockOnly
    );
}