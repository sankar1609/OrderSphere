package com.ordersphere.inventory.exception;

/** A vendor tried to change a product another vendor (or nobody known) created. */
public class ProductOwnershipException extends RuntimeException {

  public ProductOwnershipException(String sku) {
    super("Only the vendor who created " + sku + " (or an admin) can restock it");
  }
}
