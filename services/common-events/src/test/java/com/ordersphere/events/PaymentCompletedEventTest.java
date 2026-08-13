package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentCompletedEventTest {

  @Test
  void carriesPaymentAndOrderIdAndEventType() {
    PaymentCompletedEvent event = new PaymentCompletedEvent(1L, 100L, "alice");

    assertThat(event.getPaymentId()).isEqualTo(1L);
    assertThat(event.getOrderId()).isEqualTo(100L);
    assertThat(event.getCustomerUsername()).isEqualTo("alice");
    assertThat(event.getEventType()).isEqualTo("PaymentCompletedEvent");
  }
}
