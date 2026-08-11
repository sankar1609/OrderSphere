package com.ordersphere.events;

import lombok.Getter;

@Getter
public class PaymentFailedEvent extends BaseEvent {

  private final Long paymentId;
  private final Long orderId;
  private final String reason;

  public PaymentFailedEvent(Long paymentId, Long orderId, String reason) {
    super("PaymentFailedEvent");
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.reason = reason;
  }
}
