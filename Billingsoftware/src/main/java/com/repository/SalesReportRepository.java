package com.repository;
 
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
 
import com.entity.SalesReport;
 
@Repository
public interface SalesReportRepository extends JpaRepository<SalesReport, Long> {
 
    // Daily Sales Report aggregation
    @Query(value = "SELECT DATE(o.created_at) as reportDate, " +
                   "SUM(o.grand_total) as totalSales, " +
                   "COUNT(o.id) as totalOrders, " +
                   "SUM(o.gst_amount) as totalTax, " +
                   "SUM(o.discount) as totalDiscount " +
                   "FROM tbl_orderhistory o " +
                   "WHERE o.user_id = :userId " +
                   "AND (:startDate IS NULL OR DATE(o.created_at) >= :startDate) " +
                   "AND (:endDate IS NULL OR DATE(o.created_at) <= :endDate) " +
                   "GROUP BY DATE(o.created_at) " +
                   "ORDER BY DATE(o.created_at) ASC", nativeQuery = true)
    List<Object[]> getDailySalesReport(
            @Param("userId") String userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
 
    // Monthly Sales Report aggregation
    @Query(value = "SELECT DATE_FORMAT(o.created_at, '%Y-%m') as reportMonth, " +
                   "SUM(o.grand_total) as totalSales, " +
                   "COUNT(o.id) as totalOrders, " +
                   "SUM(o.gst_amount) as totalTax, " +
                   "SUM(o.discount) as totalDiscount " +
                   "FROM tbl_orderhistory o " +
                   "WHERE o.user_id = :userId " +
                   "AND (:startDate IS NULL OR DATE(o.created_at) >= :startDate) " +
                   "AND (:endDate IS NULL OR DATE(o.created_at) <= :endDate) " +
                   "GROUP BY DATE_FORMAT(o.created_at, '%Y-%m') " +
                   "ORDER BY reportMonth ASC", nativeQuery = true)
    List<Object[]> getMonthlySalesReport(
            @Param("userId") String userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
 
    // Yearly Sales Report aggregation
    @Query(value = "SELECT YEAR(o.created_at) as reportYear, " +
                   "SUM(o.grand_total) as totalSales, " +
                   "COUNT(o.id) as totalOrders, " +
                   "SUM(o.gst_amount) as totalTax, " +
                   "SUM(o.discount) as totalDiscount " +
                   "FROM tbl_orderhistory o " +
                   "WHERE o.user_id = :userId " +
                   "AND (:startDate IS NULL OR DATE(o.created_at) >= :startDate) " +
                   "AND (:endDate IS NULL OR DATE(o.created_at) <= :endDate) " +
                   "GROUP BY YEAR(o.created_at) " +
                   "ORDER BY reportYear ASC", nativeQuery = true)
    List<Object[]> getYearlySalesReport(
            @Param("userId") String userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
 
    // GST Report aggregation
    @Query(value = "SELECT oi.gst_percentage as gstRate, " +
                   "SUM(oi.price * oi.quantity - oi.discount) as taxableAmount, " +
                   "SUM(oi.gst_amount) / 2 as cgstAmount, " +
                   "SUM(oi.gst_amount) / 2 as sgstAmount, " +
                   "SUM(oi.gst_amount) as totalGst, " +
                   "SUM(oi.price * oi.quantity - oi.discount + oi.gst_amount) as totalSales " +
                   "FROM tbl_orderhistory o " +
                   "JOIN tbl_order_items oi ON o.id = oi.order_id " +
                   "WHERE o.user_id = :userId " +
                   "AND (:startDate IS NULL OR DATE(o.created_at) >= :startDate) " +
                   "AND (:endDate IS NULL OR DATE(o.created_at) <= :endDate) " +
                   "GROUP BY oi.gst_percentage " +
                   "ORDER BY oi.gst_percentage ASC", nativeQuery = true)
    List<Object[]> getGSTReport(
            @Param("userId") String userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
