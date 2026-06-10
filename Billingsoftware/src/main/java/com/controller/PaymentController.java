package com.controller;
import com.service.impl.AppUserDetailsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.io.OrderResponse;
import com.io.PaymentRequest;
import com.io.PaymentVerificationRequest;
import com.io.RazorpayOrderResponse;
import com.razorpay.RazorpayException;
import com.service.RazorpayService;
import com.service.OrderService;

@RestController
@RequestMapping("/payments")
public class PaymentController {

   

    private final RazorpayService razorpayService;
    private final OrderService orderService;
	private AppUserDetailsService appUserDetailsService;

    public PaymentController(RazorpayService razorpayService,
                             OrderService orderService, AppUserDetailsService appUserDetailsService) {
        this.razorpayService = razorpayService;
        this.orderService = orderService;
        this.appUserDetailsService = appUserDetailsService;
    }

    @PostMapping("/create-order")
    @ResponseStatus(HttpStatus.CREATED)
    public RazorpayOrderResponse createRazorpayOrder(
            @RequestBody PaymentRequest request
    ) throws RazorpayException {

        return razorpayService.createOrder(
                request.getAmount(),
                request.getCurrency()
        );
    }
    
    @PostMapping("/verify")
    public OrderResponse verifyPayment(@RequestBody PaymentVerificationRequest request) {
    	return orderService.verifyPayment(request);
     	
    }
    
   
}