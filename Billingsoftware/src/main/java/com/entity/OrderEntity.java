package com.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.io.PaymentDetails;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing a Customer Order in the billing system.
 * Contains customer details, pricing summaries, payment metadata, and ordered items.
 */
@Entity
@Table(name = "tbl_orderhistory")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderEntity {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id; // Auto-incrementing database primary key
	
	private String orderId; // Unique business order identifier (e.g. ORD17172023...)
	private String customerName; // Name of the customer placing the order
	private String phoneNumber; // Customer's mobile number
	private Double subtotal; // Sum of item prices before tax
	private Double tax; // Calculated tax amount (usually 1%)
	private Double grandTotal; // Total amount payable (subtotal + tax)
	private Double discount; // Overall discount amount
	private Double gstAmount; // Total GST amount
	
	// Saved creation timestamp. Mapped to lowercase camelCase to match repository method naming.
	private LocalDateTime createdAt;
	
	// Unidirectional One-to-Many mapping to OrderItemEntity.
	// Linked using the order_id foreign key column in tbl_order_items table.
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "order_id")
	@Builder.Default
	private List<OrderItemEntity> item = new ArrayList<>();
	
	// Embeddable details of the payment transaction (e.g., Razorpay ids and verification status)
	@Embedded
	private PaymentDetails paymentDetails;
	
	// Enum value representing payment method (CASH or UPI)
	@Enumerated(EnumType.STRING)
	private PaymentMethod paymentMethod;

	@Column(name = "user_id")
	private String userId;

	private String pdfUrl;
	
	/**
	 * Callback hook executed before persisting the entity.
	 * Automatically generates a unique business order ID and sets the current timestamp.
	 */
	@PrePersist
	protected void onCreate() {
		this.orderId = "ORD"+System.currentTimeMillis();
		this.createdAt = LocalDateTime.now();
	}
}
