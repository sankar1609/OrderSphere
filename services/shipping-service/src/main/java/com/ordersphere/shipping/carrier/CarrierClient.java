package com.ordersphere.shipping.carrier;

import com.ordersphere.shipping.domain.ShipmentStatus;

public interface CarrierClient {

  CarrierUpdate nextStage(ShipmentStatus currentStatus);
}
