package com.ordersphere.shipping.carrier;

import com.ordersphere.shipping.domain.ShipmentStatus;
import org.springframework.stereotype.Component;

@Component
public class StubCarrierClient implements CarrierClient {

  @Override
  public CarrierUpdate nextStage(ShipmentStatus currentStatus) {
    return switch (currentStatus) {
      case CREATED -> new CarrierUpdate(ShipmentStatus.PICKED, "Origin facility");
      case PICKED -> new CarrierUpdate(ShipmentStatus.IN_TRANSIT, "Regional sorting facility");
      case IN_TRANSIT -> new CarrierUpdate(ShipmentStatus.DELIVERED, "Destination");
      case DELIVERED -> throw new IllegalStateException("Shipment is already delivered");
      case CANCELLED -> throw new IllegalStateException("Shipment is cancelled");
    };
  }
}
