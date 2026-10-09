package com.ordersphere.events;

import java.time.Instant;
import lombok.Getter;

@Getter
public class DeliveryConfirmedEvent extends BaseEvent implements OrderScoped {

  private final Long shipmentId;
  private final Long orderId;
  private final String customerUsername;
  private final Instant deliveredAt;

  public DeliveryConfirmedEvent(
      Long shipmentId, Long orderId, String customerUsername, Instant deliveredAt) {
    super("DeliveryConfirmedEvent");
    this.shipmentId = shipmentId;
    this.orderId = orderId;
    this.customerUsername = customerUsername;
    this.deliveredAt = deliveredAt;
  }
}
