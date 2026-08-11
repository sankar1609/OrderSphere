package com.ordersphere.events;

import lombok.Getter;

@Getter
public class ShipmentPickedEvent extends BaseEvent {

  private final Long shipmentId;
  private final Long orderId;

  public ShipmentPickedEvent(Long shipmentId, Long orderId) {
    super("ShipmentPickedEvent");
    this.shipmentId = shipmentId;
    this.orderId = orderId;
  }
}
