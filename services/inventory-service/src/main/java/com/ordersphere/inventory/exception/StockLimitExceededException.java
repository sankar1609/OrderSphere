package com.ordersphere.inventory.exception;

public class StockLimitExceededException extends RuntimeException {

  public StockLimitExceededException(String sku, int max) {
    super("Restock would take " + sku + " over the maximum of " + max + " units on hand");
  }
}
