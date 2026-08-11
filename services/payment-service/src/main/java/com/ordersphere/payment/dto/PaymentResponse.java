package com.ordersphere.payment.dto;

import com.ordersphere.payment.domain.Payment;
import com.ordersphere.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
    Long id,
    Long orderId,
    String customerUsername,
    Long paymentMethodId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    Instant createdAt,
    Instant updatedAt) {

  public static PaymentResponse from(Payment payment) {
    return new PaymentResponse(
        payment.getId(),
        payment.getOrderId(),
        payment.getCustomerUsername(),
        payment.getPaymentMethod().getId(),
        payment.getAmount(),
        payment.getCurrency(),
        payment.getStatus(),
        payment.getCreatedAt(),
        payment.getUpdatedAt());
  }
}
