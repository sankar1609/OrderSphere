package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InventoryReleasedEventTest {

  @Test
  void carriesOrderIdReasonAndEventType() {
    InventoryReleasedEvent event =
        new InventoryReleasedEvent(1L, InventoryReleasedEvent.Reason.EXPIRED);

    assertThat(event.getOrderId()).isEqualTo(1L);
    assertThat(event.getReason()).isEqualTo(InventoryReleasedEvent.Reason.EXPIRED);
    assertThat(event.getEventType()).isEqualTo("InventoryReleasedEvent");
  }
}
