package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShipmentPickedEventTest {

  @Test
  void carriesShipmentAndOrderIdAndEventType() {
    ShipmentPickedEvent event = new ShipmentPickedEvent(1L, 100L);

    assertThat(event.getShipmentId()).isEqualTo(1L);
    assertThat(event.getOrderId()).isEqualTo(100L);
    assertThat(event.getEventType()).isEqualTo("ShipmentPickedEvent");
  }
}
