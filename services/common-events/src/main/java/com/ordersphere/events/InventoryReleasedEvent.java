package com.ordersphere.events;

import lombok.Getter;

@Getter
public class InventoryReleasedEvent extends BaseEvent implements OrderScoped {

  public enum Reason {
    MANUAL,
    EXPIRED
  }

  private final Long orderId;
  private final Reason reason;

  public InventoryReleasedEvent(Long orderId, Reason reason) {
    super("InventoryReleasedEvent");
    this.orderId = orderId;
    this.reason = reason;
  }
}
