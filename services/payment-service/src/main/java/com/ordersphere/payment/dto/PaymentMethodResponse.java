package com.ordersphere.payment.dto;

import com.ordersphere.payment.domain.PaymentMethod;
import com.ordersphere.payment.domain.PaymentMethodType;
import java.time.Instant;

public record PaymentMethodResponse(
    Long id, PaymentMethodType type, String token, Instant createdAt) {

  public static PaymentMethodResponse from(PaymentMethod paymentMethod) {
    return new PaymentMethodResponse(
        paymentMethod.getId(),
        paymentMethod.getType(),
        paymentMethod.getToken(),
        paymentMethod.getCreatedAt());
  }
}
