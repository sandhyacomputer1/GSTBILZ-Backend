package com.io;

import java.time.LocalDateTime;
import java.util.List;

import com.entity.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderResponse {
	
	private String orderId;
	private String customerName;
	private String phoneNumber;
	private List<OrderResponse.OrderItemResponse> item;
	private Double subtotal;
	private Double tax;
	private Double grandTotal;
	private Double discount;
	private Double gstAmount;
	private PaymentMethod paymentMethod;
	private LocalDateTime createdAt;
	private PaymentDetails paymentDetails;
	private String pdfUrl;
	
	
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	@Builder
	public static class OrderItemResponse {
		
		private String itemId;
		private String name;
		private Double price;
		private Integer quantity;
		private Double discount;
		private Double gstAmount;
		private Double gstPercentage;
	}
}
