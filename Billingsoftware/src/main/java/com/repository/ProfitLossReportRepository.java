package com.repository;
 
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
 
import com.entity.ProfitLossReport;
 
@Repository
public interface ProfitLossReportRepository extends JpaRepository<ProfitLossReport, Long> {
 
    @Query(value = "SELECT DATE_FORMAT(o.created_at, '%Y-%m') as reportPeriod, " +
                   "SUM(o.grand_total) as totalRevenue, " +
                   "SUM((SELECT COALESCE(SUM(oi.quantity * COALESCE(i.purchase_price, 0)), 0) " +
                   "     FROM tbl_order_items oi " +
                   "     LEFT JOIN tbl_items i ON oi.item_id = i.item_id " +
                   "     WHERE oi.order_id = o.id)) as totalCost " +
                   "FROM tbl_orderhistory o " +
                   "WHERE o.user_id = :userId " +
                   "AND (:startDate IS NULL OR DATE(o.created_at) >= :startDate) " +
                   "AND (:endDate IS NULL OR DATE(o.created_at) <= :endDate) " +
                   "GROUP BY DATE_FORMAT(o.created_at, '%Y-%m') " +
                   "ORDER BY reportPeriod ASC", nativeQuery = true)
    List<Object[]> getProfitLossReport(
            @Param("userId") String userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
