package com.ordersphere.events;

import lombok.Getter;

@Getter
public class ShipmentPickedEvent extends BaseEvent implements OrderScoped {

  private final Long shipmentId;
  private final Long orderId;
  private final String customerUsername;

  public ShipmentPickedEvent(Long shipmentId, Long orderId, String customerUsername) {
    super("ShipmentPickedEvent");
    this.shipmentId = shipmentId;
    this.orderId = orderId;
    this.customerUsername = customerUsername;
  }
}
