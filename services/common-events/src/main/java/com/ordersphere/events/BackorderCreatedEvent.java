package com.ordersphere.events;

import lombok.Getter;

@Getter
public class BackorderCreatedEvent extends BaseEvent {

  private final Long orderId;
  private final String sku;
  private final int quantity;

  public BackorderCreatedEvent(Long orderId, String sku, int quantity) {
    super("BackorderCreatedEvent");
    this.orderId = orderId;
    this.sku = sku;
    this.quantity = quantity;
  }
}
