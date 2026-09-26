package com.ordersphere.events;

import lombok.Getter;

@Getter
public class ShipmentInTransitEvent extends BaseEvent {

  private final Long shipmentId;
  private final Long orderId;
  private final String customerUsername;

  public ShipmentInTransitEvent(Long shipmentId, Long orderId, String customerUsername) {
    super("ShipmentInTransitEvent");
    this.shipmentId = shipmentId;
    this.orderId = orderId;
    this.customerUsername = customerUsername;
  }
}
