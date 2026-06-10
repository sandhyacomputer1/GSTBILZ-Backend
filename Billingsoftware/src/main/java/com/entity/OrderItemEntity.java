package com.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;

/**
 * Entity representing an individual line item inside a customer order.
 * Stores a snapshot of the purchased item details (id, name, price, quantity)
 * at the time the order was placed to keep historical accuracy.
 */
@Entity
@Table(name = "tbl_order_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id; // Auto-incrementing database primary key for the row
	
	private String itemId; // UUID referring to the original item/product
	private String name; // Snapshot of the item name
	private Double price; // Snapshot of the item unit price at time of purchase
	private Integer quantity; // Purchased quantity of this item
	private Double discount; // Discount applied to this line item
	private Double gstAmount; // GST amount calculated for this line item
	private Double gstPercentage; // GST rate for this line item
}
