package com.ordersphere.events;

import lombok.Getter;

@Getter
public class OrderCancelledEvent extends BaseEvent implements OrderScoped {

  public enum Reason {
    CUSTOMER_REQUESTED,
    INVENTORY_UNAVAILABLE,
    PAYMENT_FAILED
  }

  private final Long orderId;
  private final Reason reason;
  private final String customerUsername;

  public OrderCancelledEvent(Long orderId, Reason reason, String customerUsername) {
    super("OrderCancelledEvent");
    this.orderId = orderId;
    this.reason = reason;
    this.customerUsername = customerUsername;
  }
}
