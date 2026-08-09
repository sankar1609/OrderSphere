package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StockLowEventTest {

  @Test
  void carriesSkuQuantitiesAndEventType() {
    StockLowEvent event = new StockLowEvent("SKU-1", 2, 5);

    assertThat(event.getSku()).isEqualTo("SKU-1");
    assertThat(event.getAvailableQuantity()).isEqualTo(2);
    assertThat(event.getReorderThreshold()).isEqualTo(5);
    assertThat(event.getEventType()).isEqualTo("StockLowEvent");
  }
}
