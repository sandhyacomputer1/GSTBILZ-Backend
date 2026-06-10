package com.io;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for receiving new order requests from the frontend client.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderRequest {
	
	private String customerName; // Customer's full name
	private String phoneNumber; // Customer's contact number
	private List<OrderItemRequest> cartItems; // List of items added to the cart
	private Double subtotal; // Cost summation before taxes
	private Double tax; // Calculated tax for the order
	private Double grandTotal; // Net checkout price (subtotal + tax)
	private Double discount; // Final overall discount amount
	private Double gstAmount; // Total GST amount
	private String paymentMethod; // Payment method selected: CASH or UPI
	
	/**
	 * Inner DTO representing a line item in the order request cart.
	 */
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class OrderItemRequest {
		
		private String itemId; // ID of the product
		private String name; // Name of the product
		private Double price; // Selling price of the item
		private Integer quantity; // Quantity ordered
		private Double discount; // Item-level discount
		private Double gstAmount; // Item-level calculated GST
		private Double gstPercentage; // Item-level GST rate
	}	

}
