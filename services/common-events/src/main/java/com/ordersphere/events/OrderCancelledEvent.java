package com.ordersphere.events;

import lombok.Getter;

@Getter
public class OrderCancelledEvent extends BaseEvent {

  public enum Reason {
    CUSTOMER_REQUESTED,
    INVENTORY_UNAVAILABLE
  }

  private final Long orderId;
  private final Reason reason;

  public OrderCancelledEvent(Long orderId, Reason reason) {
    super("OrderCancelledEvent");
    this.orderId = orderId;
    this.reason = reason;
  }
}
