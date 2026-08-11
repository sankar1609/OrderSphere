package com.ordersphere.events;

import lombok.Getter;

@Getter
public class ShipmentInTransitEvent extends BaseEvent {

  private final Long shipmentId;
  private final Long orderId;

  public ShipmentInTransitEvent(Long shipmentId, Long orderId) {
    super("ShipmentInTransitEvent");
    this.shipmentId = shipmentId;
    this.orderId = orderId;
  }
}
