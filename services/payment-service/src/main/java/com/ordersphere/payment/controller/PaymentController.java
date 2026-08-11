package com.ordersphere.payment.controller;

import com.ordersphere.payment.dto.CreatePaymentRequest;
import com.ordersphere.payment.dto.PaymentResponse;
import com.ordersphere.payment.dto.RefundRequest;
import com.ordersphere.payment.dto.RefundResponse;
import com.ordersphere.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @PostMapping
  public ResponseEntity<PaymentResponse> initiatePayment(
      @Valid @RequestBody CreatePaymentRequest request, Authentication authentication) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(paymentService.initiatePayment(authentication.getName(), request));
  }

  @GetMapping("/{paymentId}")
  public PaymentResponse getPayment(@PathVariable Long paymentId, Authentication authentication) {
    return paymentService.getPayment(authentication.getName(), isAdmin(authentication), paymentId);
  }

  @PostMapping("/{paymentId}/refund")
  public ResponseEntity<RefundResponse> refundPayment(
      @PathVariable Long paymentId,
      @Valid @RequestBody RefundRequest request,
      Authentication authentication) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            paymentService.refundPayment(
                authentication.getName(), isAdmin(authentication), paymentId, request));
  }

  private boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
