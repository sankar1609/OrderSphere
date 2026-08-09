package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class InventoryReservedEventTest {

  @Test
  void carriesOrderIdAndReservedQuantitiesAndEventType() {
    InventoryReservedEvent event = new InventoryReservedEvent(1L, Map.of("SKU-1", 5));

    assertThat(event.getOrderId()).isEqualTo(1L);
    assertThat(event.getReservedQuantitiesBySku()).containsEntry("SKU-1", 5);
    assertThat(event.getEventType()).isEqualTo("InventoryReservedEvent");
  }
}
