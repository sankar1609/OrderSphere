package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StockLowEventTest {

  @Test
  void carriesProductStockOwnerAndEventType() {
    StockLowEvent event = new StockLowEvent("SKU-1", "Widget", 2, 5, "vendor1");

    assertThat(event.getSku()).isEqualTo("SKU-1");
    assertThat(event.getProductName()).isEqualTo("Widget");
    assertThat(event.getAvailableQuantity()).isEqualTo(2);
    assertThat(event.getReorderThreshold()).isEqualTo(5);
    assertThat(event.getOwnerUsername()).isEqualTo("vendor1");
    assertThat(event.getEventType()).isEqualTo("StockLowEvent");
  }
}
