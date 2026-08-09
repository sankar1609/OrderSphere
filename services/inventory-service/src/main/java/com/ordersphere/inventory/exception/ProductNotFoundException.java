package com.ordersphere.inventory.exception;

public class ProductNotFoundException extends RuntimeException {

  public ProductNotFoundException(String sku) {
    super("No product found with sku: " + sku);
  }
}
