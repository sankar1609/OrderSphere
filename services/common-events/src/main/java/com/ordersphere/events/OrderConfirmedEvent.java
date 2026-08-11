package com.ordersphere.events;

import lombok.Getter;

@Getter
public class OrderConfirmedEvent extends BaseEvent {

  private final Long orderId;

  public OrderConfirmedEvent(Long orderId) {
    super("OrderConfirmedEvent");
    this.orderId = orderId;
  }
}
