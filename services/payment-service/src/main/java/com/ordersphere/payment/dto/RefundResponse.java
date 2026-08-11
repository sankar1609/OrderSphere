package com.ordersphere.payment.dto;

import com.ordersphere.payment.domain.Refund;
import com.ordersphere.payment.domain.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record RefundResponse(
    Long id,
    Long paymentId,
    BigDecimal amount,
    String reason,
    RefundStatus status,
    Instant createdAt) {

  public static RefundResponse from(Refund refund) {
    return new RefundResponse(
        refund.getId(),
        refund.getPayment().getId(),
        refund.getAmount(),
        refund.getReason(),
        refund.getStatus(),
        refund.getCreatedAt());
  }
}
