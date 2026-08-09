package com.ordersphere.inventory.exception;

public class DuplicateSkuException extends RuntimeException {

  public DuplicateSkuException(String sku) {
    super("Product sku already exists: " + sku);
  }
}
