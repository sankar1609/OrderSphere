package com.ordersphere.events;

import lombok.Getter;

@Getter
public class StockLowEvent extends BaseEvent {

  private final String sku;
  private final int availableQuantity;
  private final int reorderThreshold;

  public StockLowEvent(String sku, int availableQuantity, int reorderThreshold) {
    super("StockLowEvent");
    this.sku = sku;
    this.availableQuantity = availableQuantity;
    this.reorderThreshold = reorderThreshold;
  }
}
