package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderCancelledEventTest {

  @Test
  void carriesOrderIdReasonAndEventType() {
    OrderCancelledEvent event =
        new OrderCancelledEvent(1L, OrderCancelledEvent.Reason.INVENTORY_UNAVAILABLE);

    assertThat(event.getOrderId()).isEqualTo(1L);
    assertThat(event.getReason()).isEqualTo(OrderCancelledEvent.Reason.INVENTORY_UNAVAILABLE);
    assertThat(event.getEventType()).isEqualTo("OrderCancelledEvent");
  }

  @Test
  void supportsPaymentFailedReason() {
    OrderCancelledEvent event =
        new OrderCancelledEvent(1L, OrderCancelledEvent.Reason.PAYMENT_FAILED);

    assertThat(event.getReason()).isEqualTo(OrderCancelledEvent.Reason.PAYMENT_FAILED);
  }
}
