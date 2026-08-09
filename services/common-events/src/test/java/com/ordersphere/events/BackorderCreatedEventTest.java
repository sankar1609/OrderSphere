package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BackorderCreatedEventTest {

  @Test
  void carriesOrderIdSkuQuantityAndEventType() {
    BackorderCreatedEvent event = new BackorderCreatedEvent(1L, "SKU-1", 3);

    assertThat(event.getOrderId()).isEqualTo(1L);
    assertThat(event.getSku()).isEqualTo("SKU-1");
    assertThat(event.getQuantity()).isEqualTo(3);
    assertThat(event.getEventType()).isEqualTo("BackorderCreatedEvent");
  }
}
