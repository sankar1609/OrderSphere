package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeliveryConfirmedEventTest {

  @Test
  void carriesShipmentOrderDeliveredAtAndEventType() {
    Instant deliveredAt = Instant.now();
    DeliveryConfirmedEvent event = new DeliveryConfirmedEvent(1L, 100L, "alice", deliveredAt);

    assertThat(event.getShipmentId()).isEqualTo(1L);
    assertThat(event.getOrderId()).isEqualTo(100L);
    assertThat(event.getCustomerUsername()).isEqualTo("alice");
    assertThat(event.getDeliveredAt()).isEqualTo(deliveredAt);
    assertThat(event.getEventType()).isEqualTo("DeliveryConfirmedEvent");
  }
}
