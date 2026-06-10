package com.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.entity.OrderEntity;

public interface OrderEntityRepository extends JpaRepository<OrderEntity, Long>{

	Optional<OrderEntity> findByOrderId(String orderId);
	Optional<OrderEntity> findByOrderIdAndUserId(String orderId, String userId);
	
	List<OrderEntity> findAllByOrderByCreatedAtDesc();
	List<OrderEntity> findByUserIdOrderByCreatedAtDesc(String userId);
	
	@Query("SELECT SUM(o.grandTotal) FROM OrderEntity o WHERE DATE(o.createdAt) = :date AND (:userId IS NULL OR o.userId = :userId)")
	Double sumSalesByDate(@Param("date") LocalDate date, @Param("userId") String userId);

	@Query("SELECT COUNT(o) FROM OrderEntity o WHERE DATE(o.createdAt) = :date AND (:userId IS NULL OR o.userId = :userId)")
	Long countByOrderDate(@Param("date") LocalDate date, @Param("userId") String userId);

	@Query("SELECT o FROM OrderEntity o WHERE :userId IS NULL OR o.userId = :userId ORDER BY o.createdAt DESC")
	List<OrderEntity> findRecentOrders(@Param("userId") String userId, PageRequest pageRequest);

	@Query("SELECT COUNT(DISTINCT o.phoneNumber) FROM OrderEntity o")
	Long countUniqueCustomers();

	@Query("SELECT SUM(o.grandTotal) FROM OrderEntity o")
	Double sumAllSales();
}
