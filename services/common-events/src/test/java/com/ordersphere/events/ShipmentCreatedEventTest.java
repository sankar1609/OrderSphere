package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShipmentCreatedEventTest {

  @Test
  void carriesShipmentFieldsAndEventType() {
    ShipmentCreatedEvent event = new ShipmentCreatedEvent(1L, 100L, "123 Main St");

    assertThat(event.getShipmentId()).isEqualTo(1L);
    assertThat(event.getOrderId()).isEqualTo(100L);
    assertThat(event.getDestination()).isEqualTo("123 Main St");
    assertThat(event.getEventType()).isEqualTo("ShipmentCreatedEvent");
  }
}
