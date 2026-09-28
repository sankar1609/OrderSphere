package com.ordersphere.payment.dto;

import com.ordersphere.payment.domain.Payment;
import com.ordersphere.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code checkoutUrl} is where the customer pays; it is only meaningful while the payment is
 * PENDING. {@code failureReason} explains a FAILED payment.
 */
public record PaymentResponse(
    Long id,
    Long orderId,
    String customerUsername,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    String checkoutUrl,
    String failureReason,
    Instant createdAt,
    Instant updatedAt) {

  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getOrderId(),
        payment.getCustomerUsername(),
        payment.getAmount(),
        payment.getCurrency(),
        payment.getStatus(),
        payment.getStatus() == PaymentStatus.PENDING ? payment.getCheckoutUrl() : null,
        payment.getFailureReason(),
        payment.getCreatedAt(),
        payment.getUpdatedAt());
  }
}
