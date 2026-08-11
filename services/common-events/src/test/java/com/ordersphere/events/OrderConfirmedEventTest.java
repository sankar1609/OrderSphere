package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderConfirmedEventTest {

  @Test
  void carriesOrderIdAndEventType() {
    OrderConfirmedEvent event = new OrderConfirmedEvent(1L);

    assertThat(event.getOrderId()).isEqualTo(1L);
    assertThat(event.getEventType()).isEqualTo("OrderConfirmedEvent");
  }
}
