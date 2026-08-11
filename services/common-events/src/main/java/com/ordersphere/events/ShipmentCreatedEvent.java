package com.ordersphere.events;

import lombok.Getter;

@Getter
public class ShipmentCreatedEvent extends BaseEvent {

  private final Long shipmentId;
  private final Long orderId;
  private final String destination;

  public ShipmentCreatedEvent(Long shipmentId, Long orderId, String destination) {
    super("ShipmentCreatedEvent");
    this.shipmentId = shipmentId;
    this.orderId = orderId;
    this.destination = destination;
  }
}
