package com.ordersphere.events;

import lombok.Getter;

@Getter
public class PaymentFailedEvent extends BaseEvent implements OrderScoped {

  private final Long paymentId;
  private final Long orderId;
  private final String reason;
  private final String customerUsername;

  public PaymentFailedEvent(Long paymentId, Long orderId, String reason, String customerUsername) {
    super("PaymentFailedEvent");
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.reason = reason;
    this.customerUsername = customerUsername;
  }
}
