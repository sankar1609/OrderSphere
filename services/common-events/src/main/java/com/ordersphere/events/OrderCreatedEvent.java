package com.ordersphere.events;

import java.util.Map;
import lombok.Getter;

@Getter
public class OrderCreatedEvent extends BaseEvent {

  private final Long orderId;
  private final String customerUsername;
  private final Map<String, Integer> quantitiesBySku;

  public OrderCreatedEvent(
      Long orderId, String customerUsername, Map<String, Integer> quantitiesBySku) {
    super("OrderCreatedEvent");
    this.orderId = orderId;
    this.customerUsername = customerUsername;
    this.quantitiesBySku = quantitiesBySku;
  }
}
