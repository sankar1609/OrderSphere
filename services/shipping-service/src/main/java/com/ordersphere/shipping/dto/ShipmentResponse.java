package com.ordersphere.shipping.dto;

import com.ordersphere.shipping.domain.Shipment;
import com.ordersphere.shipping.domain.ShipmentStatus;
import com.ordersphere.shipping.domain.ShipmentType;
import java.time.Instant;

public record ShipmentResponse(
    Long id,
    Long orderId,
    ShipmentType type,
    ShipmentStatus status,
    String carrier,
    String trackingNumber,
    String destination,
    Long parentShipmentId,
    Instant createdAt,
    Instant updatedAt,
    Instant deliveredAt) {

  public static ShipmentResponse from(Shipment shipment) {
    return new ShipmentResponse(
        shipment.getId(),
        shipment.getOrderId(),
        shipment.getType(),
        shipment.getStatus(),
        shipment.getCarrier(),
        shipment.getTrackingNumber(),
        shipment.getDestination(),
        shipment.getParentShipmentId(),
        shipment.getCreatedAt(),
        shipment.getUpdatedAt(),
        shipment.getDeliveredAt());
  }
}
