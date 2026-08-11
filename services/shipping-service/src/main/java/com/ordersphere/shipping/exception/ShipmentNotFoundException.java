package com.ordersphere.shipping.exception;

public class ShipmentNotFoundException extends RuntimeException {

  public ShipmentNotFoundException(Long id) {
    super("No shipment found with id: " + id);
  }
}
