package com.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.entity.OrderEntity;
import com.entity.OrderItemEntity;
import com.entity.PaymentMethod;
import com.io.OrderRequest;
import com.io.OrderResponse;
import com.io.PaymentDetails;
import com.io.PaymentVerificationRequest;
import com.repository.OrderEntityRepository;
import com.repository.UserRepository;
import com.service.OrderService;
import com.entity.UserEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Implementation service handling core Customer Order operations,
 * including creation, deletion, list fetches, and payment status updates.
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
	private final OrderEntityRepository orderEntityRepository;
	private final UserRepository userRepository;
	private final com.service.InvoiceService invoiceService;
	private final com.service.WhatsAppService whatsAppService;

	private UserEntity getLoggedInUser() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		return userRepository.findByEmail(email)
				.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}

	/**
	 * Creates and persists a new customer order.
	 * Maps request DTO parameters to OrderEntity, resolves payment status based on cash/UPI selection,
	 * attaches line items, saves the entity, and formats the response DTO.
	 */
	@Override
	public OrderResponse createdOrder(OrderRequest request) {
		UserEntity currentUser = getLoggedInUser();
		
		// 1. Convert root DTO fields to entity properties
		OrderEntity newOrder = convertToOrderEntity(request);
		newOrder.setUserId(currentUser.getTenantId());
		
		// 2. Automatically mark Cash payments as COMPLETED; UPI payments remain PENDING until verified
		PaymentDetails paymentDetails = new PaymentDetails();
		paymentDetails.setStatus(newOrder.getPaymentMethod() == PaymentMethod.CASH ? 
				PaymentDetails.PaymentStatus.COMPLETED : PaymentDetails.PaymentStatus.PENDING);
		newOrder.setPaymentDetails(paymentDetails);
		
		// 3. Map line item request payloads to order line item entities
		List<OrderItemEntity> orderItems = request.getCartItems().stream()
				.map(this::convertToOrderItemEntity)
				.collect(Collectors.toList());
		newOrder.setItem(orderItems);
		
		// 4. Save and flush the order to generate automatic IDs and persist in database
		newOrder = orderEntityRepository.save(newOrder);

		try {
			invoiceService.generateAndSaveInvoice(newOrder);
		} catch (Exception ex) {
			System.err.println("Warning: Automated PDF invoice generation failed during checkout: " + ex.getMessage());
		}

		try {
			final String orderId = newOrder.getOrderId();
			userRepository.findByUserId(newOrder.getUserId()).ifPresent(shopOwner -> {
				if (shopOwner.isWhatsappEnabled() && shopOwner.isWhatsappAutoSend()) {
					whatsAppService.sendInvoice(orderId);
				}
			});
		} catch (Exception ex) {
			System.err.println("Warning: Automated WhatsApp sending failed: " + ex.getMessage());
		}

		return convertToResponse(newOrder);
		
	}
	
	/**
	 * Helper: Maps an OrderItemRequest DTO to an OrderItemEntity.
	 */
	private OrderItemEntity convertToOrderItemEntity(OrderRequest.OrderItemRequest orderItemRequest) {
		return OrderItemEntity.builder()
			.itemId(orderItemRequest.getItemId())
			.name(orderItemRequest.getName())
			.price(orderItemRequest.getPrice())
			.quantity(orderItemRequest.getQuantity())
			.discount(orderItemRequest.getDiscount())
			.gstAmount(orderItemRequest.getGstAmount())
			.gstPercentage(orderItemRequest.getGstPercentage())
			.build();
	}

	/**
	 * Helper: Maps an OrderEntity database entity to an OrderResponse DTO.
	 */
	private OrderResponse convertToResponse(OrderEntity newOrder) {
		return OrderResponse.builder()
				.orderId(newOrder.getOrderId())
				.customerName(newOrder.getCustomerName())
				.phoneNumber(newOrder.getPhoneNumber())
				.subtotal(newOrder.getSubtotal())
				.tax(newOrder.getTax())
				.grandTotal(newOrder.getGrandTotal())
				.discount(newOrder.getDiscount())
				.gstAmount(newOrder.getGstAmount())
				.paymentMethod(newOrder.getPaymentMethod())
				.item(newOrder.getItem() != null ? newOrder.getItem().stream()
						.map(this::convertToItemResponse)
						.collect(Collectors.toList()) : null)
				.paymentDetails(newOrder.getPaymentDetails())
				.createdAt(newOrder.getCreatedAt())
				.pdfUrl(newOrder.getPdfUrl())
				.build();
	}

	/**
	 * Helper: Maps an OrderRequest DTO to an OrderEntity.
	 */
	private OrderEntity convertToOrderEntity(OrderRequest request) {
		return OrderEntity.builder()
				.customerName(request.getCustomerName())
				.phoneNumber(request.getPhoneNumber())
				.subtotal(request.getSubtotal())
				.tax(request.getTax())
				.grandTotal(request.getGrandTotal())
				.discount(request.getDiscount())
				.gstAmount(request.getGstAmount())
				.paymentMethod(request.getPaymentMethod() != null ? PaymentMethod.valueOf(request.getPaymentMethod()) : null)
				.build();
	}
	
	/**
	 * Helper: Maps an individual OrderItemEntity to an OrderItemResponse DTO.
	 */
	private OrderResponse.OrderItemResponse convertToItemResponse(OrderItemEntity orderItemEntity){
		return OrderResponse.OrderItemResponse.builder()
		.itemId(orderItemEntity.getItemId())
		.name(orderItemEntity.getName())
		.price(orderItemEntity.getPrice())
		.quantity(orderItemEntity.getQuantity())
		.discount(orderItemEntity.getDiscount())
		.gstAmount(orderItemEntity.getGstAmount())
		.gstPercentage(orderItemEntity.getGstPercentage())
		.build();		
	} 

	/**
	 * Deletes an order by business orderId. Throws exception if order is missing.
	 */
	@Override
	public void deletedOrder(String orderId) {
		UserEntity currentUser = getLoggedInUser();
		OrderEntity existingOrder = orderEntityRepository.findByOrderIdAndUserId(orderId, currentUser.getTenantId())
				.orElseThrow(() -> new RuntimeException("Order not found"));
		orderEntityRepository.delete(existingOrder);
 		
	}

	/**
	 * Fetches all orders ordered descending by creation date.
	 */
	@Override
	public List<OrderResponse> getLatestOrder() {
		UserEntity currentUser = getLoggedInUser();
		return orderEntityRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getTenantId())
				.stream()
				.map(this::convertToResponse)
				.collect(Collectors.toList());
	}

	@Override
	public OrderResponse verifyPayment(PaymentVerificationRequest request) {
		UserEntity currentUser = getLoggedInUser();
		OrderEntity existingOrder = orderEntityRepository.findByOrderIdAndUserId(request.getOrderId(), currentUser.getTenantId())
				.orElseThrow(() -> new RuntimeException("Order not found"));
		
		if(!verifyRazorpaySignature(request.getRazorpayOrderId(),
				request.getRazorpayPaymentId(),
				request.getRazorpaySignature())) {
			throw new RuntimeException("Payment verfication failed");
			
		}
		PaymentDetails paymentDetails = existingOrder.getPaymentDetails();
		paymentDetails.setRazorpayOrderId(request.getRazorpayOrderId());
		paymentDetails.setRazorpayPaymentId(request.getRazorpayPaymentId());
		paymentDetails.setRazorpaySignature(request.getRazorpaySignature());
		paymentDetails.setStatus(PaymentDetails.PaymentStatus.COMPLETED);
		
		existingOrder = orderEntityRepository.save(existingOrder);
		return convertToResponse(existingOrder);
		
	}

	private boolean verifyRazorpaySignature(String razorpayOrderId, String razorpayPaymentId,
			String razorpaySignature) {
		return true;
	}

	@Override
	public Double sumSalesByDate(LocalDate date) {
		UserEntity currentUser = getLoggedInUser();
		return orderEntityRepository.sumSalesByDate(date, currentUser.getTenantId());
	}

	@Override
	public Long countByOrderDate(LocalDate date) {
		UserEntity currentUser = getLoggedInUser();
		return orderEntityRepository.countByOrderDate(date, currentUser.getTenantId());
	}

	@Override
	public List<OrderResponse> findRecentOrders() {
		UserEntity currentUser = getLoggedInUser();
		return orderEntityRepository.findRecentOrders(currentUser.getTenantId(), PageRequest.of(0,2))
				.stream()
				.map(orderEntity -> convertToResponse(orderEntity))
				.collect(Collectors.toList());
	}
	
	

}
