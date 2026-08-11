package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShipmentInTransitEventTest {

  @Test
  void carriesShipmentAndOrderIdAndEventType() {
    ShipmentInTransitEvent event = new ShipmentInTransitEvent(1L, 100L);

    assertThat(event.getShipmentId()).isEqualTo(1L);
    assertThat(event.getOrderId()).isEqualTo(100L);
    assertThat(event.getEventType()).isEqualTo("ShipmentInTransitEvent");
  }
}
