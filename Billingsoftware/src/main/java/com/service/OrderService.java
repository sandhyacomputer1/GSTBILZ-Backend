package com.service;

import java.time.LocalDate;
import java.util.List;

import com.io.OrderRequest;
import com.io.OrderResponse;
import com.io.PaymentVerificationRequest;

public interface OrderService {
	
	OrderResponse createdOrder(OrderRequest request);
	
	void deletedOrder(String orderId);
	
	List<OrderResponse> getLatestOrder();

	OrderResponse verifyPayment(PaymentVerificationRequest request);
	
	Double sumSalesByDate(LocalDate date);
	
	Long countByOrderDate(LocalDate date);
	
	List<OrderResponse> findRecentOrders();
	
	
	

}
