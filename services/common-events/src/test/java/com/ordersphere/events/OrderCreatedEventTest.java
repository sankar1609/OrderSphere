package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OrderCreatedEventTest {

  @Test
  void carriesOrderFieldsAndEventType() {
    OrderCreatedEvent event = new OrderCreatedEvent(1L, "alice", Map.of("SKU-1", 2));

    assertThat(event.getOrderId()).isEqualTo(1L);
    assertThat(event.getCustomerUsername()).isEqualTo("alice");
    assertThat(event.getQuantitiesBySku()).containsEntry("SKU-1", 2);
    assertThat(event.getEventType()).isEqualTo("OrderCreatedEvent");
  }
}
