package com.ordersphere.events;

import lombok.Getter;

/**
 * A product's available stock just dropped to or below its reorder threshold. Published once per
 * crossing (not on every order while it stays low). {@code ownerUsername} is whoever created the
 * product - null for products that predate ownership tracking.
 */
@Getter
public class StockLowEvent extends BaseEvent {

  private final String sku;
  private final String productName;
  private final int availableQuantity;
  private final int reorderThreshold;
  private final String ownerUsername;

  public StockLowEvent(
      String sku,
      String productName,
      int availableQuantity,
      int reorderThreshold,
      String ownerUsername) {
    super("StockLowEvent");
    this.sku = sku;
    this.productName = productName;
    this.availableQuantity = availableQuantity;
    this.reorderThreshold = reorderThreshold;
    this.ownerUsername = ownerUsername;
  }
}
