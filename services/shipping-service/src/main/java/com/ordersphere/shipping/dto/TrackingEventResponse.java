package com.ordersphere.shipping.dto;

import com.ordersphere.shipping.domain.ShipmentStatus;
import com.ordersphere.shipping.domain.ShipmentTrackingEvent;
import java.time.Instant;

public record TrackingEventResponse(ShipmentStatus status, String location, Instant occurredAt) {

  public static TrackingEventResponse from(ShipmentTrackingEvent event) {
    return new TrackingEventResponse(event.getStatus(), event.getLocation(), event.getOccurredAt());
  }
}
