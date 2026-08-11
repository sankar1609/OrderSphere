package com.ordersphere.events;

import lombok.Getter;

@Getter
public class PaymentCompletedEvent extends BaseEvent {

  private final Long paymentId;
  private final Long orderId;

  public PaymentCompletedEvent(Long paymentId, Long orderId) {
    super("PaymentCompletedEvent");
    this.paymentId = paymentId;
    this.orderId = orderId;
  }
}
