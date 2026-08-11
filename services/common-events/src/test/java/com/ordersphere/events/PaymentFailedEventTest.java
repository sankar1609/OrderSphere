package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentFailedEventTest {

  @Test
  void carriesPaymentOrderReasonAndEventType() {
    PaymentFailedEvent event = new PaymentFailedEvent(1L, 100L, "declined");

    assertThat(event.getPaymentId()).isEqualTo(1L);
    assertThat(event.getOrderId()).isEqualTo(100L);
    assertThat(event.getReason()).isEqualTo("declined");
    assertThat(event.getEventType()).isEqualTo("PaymentFailedEvent");
  }
}
