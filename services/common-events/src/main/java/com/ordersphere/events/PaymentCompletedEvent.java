package com.ordersphere.events;

import lombok.Getter;

@Getter
public class PaymentCompletedEvent extends BaseEvent {

  private final Long paymentId;
  private final Long orderId;
  private final String customerUsername;

  public PaymentCompletedEvent(Long paymentId, Long orderId, String customerUsername) {
    super("PaymentCompletedEvent");
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.customerUsername = customerUsername;
  }
}
