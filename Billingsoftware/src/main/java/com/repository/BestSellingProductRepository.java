package com.repository;
 
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
 
import com.entity.BestSellingProduct;
 
@Repository
public interface BestSellingProductRepository extends JpaRepository<BestSellingProduct, Long> {
 
    @Query(value = "SELECT oi.item_id as productId, " +
                   "oi.name as productName, " +
                   "SUM(oi.quantity) as quantitySold, " +
                   "SUM(oi.quantity * oi.price - oi.discount + oi.gst_amount) as totalRevenue " +
                   "FROM tbl_orderhistory o " +
                   "JOIN tbl_order_items oi ON o.id = oi.order_id " +
                   "WHERE o.user_id = :userId " +
                   "AND (:startDate IS NULL OR DATE(o.created_at) >= :startDate) " +
                   "AND (:endDate IS NULL OR DATE(o.created_at) <= :endDate) " +
                   "GROUP BY oi.item_id, oi.name " +
                   "ORDER BY quantitySold DESC " +
                   "LIMIT :limit", nativeQuery = true)
    List<Object[]> getBestSellingProducts(
            @Param("userId") String userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("limit") int limit);
}
