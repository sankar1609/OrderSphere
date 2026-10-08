package com.ordersphere.events;

import lombok.Getter;

@Getter
public class ShipmentCreatedEvent extends BaseEvent implements OrderScoped {

  private final Long shipmentId;
  private final Long orderId;
  private final String customerUsername;
  private final String destination;

  public ShipmentCreatedEvent(
      Long shipmentId, Long orderId, String customerUsername, String destination) {
    super("ShipmentCreatedEvent");
    this.shipmentId = shipmentId;
    this.orderId = orderId;
    this.customerUsername = customerUsername;
    this.destination = destination;
  }
}
