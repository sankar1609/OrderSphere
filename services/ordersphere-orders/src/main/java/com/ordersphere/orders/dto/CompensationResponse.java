package com.ordersphere.orders.dto;

import com.ordersphere.orders.domain.Compensation;
import java.time.Instant;

/** A refund or stock release the saga owes, as shown to admins. */
public record CompensationResponse(
    Long id,
    Long orderId,
    Compensation.Type type,
    Long paymentId,
    String reason,
    Compensation.Status status,
    int attempts,
    Instant nextAttemptAt,
    String lastError,
    Instant createdAt,
    Instant updatedAt) {

  public static CompensationResponse from(Compensation compensation) {
    return new CompensationResponse(
        compensation.getId(),
        compensation.getOrderId(),
        compensation.getType(),
        compensation.getPaymentId(),
        compensation.getReason(),
        compensation.getStatus(),
        compensation.getAttempts(),
        compensation.getNextAttemptAt(),
        compensation.getLastError(),
        compensation.getCreatedAt(),
        compensation.getUpdatedAt());
  }
}
