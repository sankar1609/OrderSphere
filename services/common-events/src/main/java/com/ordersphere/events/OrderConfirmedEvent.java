package com.ordersphere.events;

import lombok.Getter;

@Getter
public class OrderConfirmedEvent extends BaseEvent implements OrderScoped {

  private final Long orderId;
  private final String customerUsername;

  public OrderConfirmedEvent(Long orderId, String customerUsername) {
    super("OrderConfirmedEvent");
    this.orderId = orderId;
    this.customerUsername = customerUsername;
  }
}
