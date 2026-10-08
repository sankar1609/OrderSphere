package com.ordersphere.payment.controller;

import com.ordersphere.payment.dto.CreatePaymentRequest;
import com.ordersphere.payment.dto.PaymentResponse;
import com.ordersphere.payment.dto.RefundRequest;
import com.ordersphere.payment.dto.RefundResponse;
import com.ordersphere.payment.logging.PaymentLogContext;
import com.ordersphere.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments", description = "Payment status; initiation and refunds are internal")
@RestController
@RequestMapping("/payments")
public class PaymentController {

  private final PaymentService paymentService;

  public PaymentController(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  /** Internal: called by ordersphere-orders for an order it created. */
  @PostMapping
  @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
  public ResponseEntity<PaymentResponse> initiatePayment(
      @Valid @RequestBody CreatePaymentRequest request) {
    try (var logContext = PaymentLogContext.forOrder(request.orderId())) {
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(paymentService.initiatePayment(request));
    }
  }

  @GetMapping("/{paymentId}")
  public PaymentResponse getPayment(@PathVariable Long paymentId, Authentication authentication) {
    return paymentService.getPayment(
        authentication.getName(), isPrivileged(authentication), paymentId);
  }

  /**
   * Internal: refunds follow an order cancellation (ordersphere-orders) or an admin decision.
   * Customers can't refund directly - they would keep the goods and get their money back.
   */
  @PostMapping("/{paymentId}/refund")
  @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
  public ResponseEntity<RefundResponse> refundPayment(
      @PathVariable Long paymentId, @Valid @RequestBody RefundRequest request) {
    try (var logContext = PaymentLogContext.forPayment(paymentId)) {
      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(paymentService.refundPayment(paymentId, request));
    }
  }

  /** ADMIN and the SERVICE identity can read any payment; customers only their own. */
  private boolean isPrivileged(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_SERVICE"));
  }
}
