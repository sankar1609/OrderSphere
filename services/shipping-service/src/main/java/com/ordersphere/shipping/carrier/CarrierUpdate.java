package com.ordersphere.shipping.carrier;

import com.ordersphere.shipping.domain.ShipmentStatus;

public record CarrierUpdate(ShipmentStatus nextStatus, String location) {}
