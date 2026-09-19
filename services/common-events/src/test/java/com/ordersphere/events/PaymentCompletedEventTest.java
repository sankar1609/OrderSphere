package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentCompletedEventTest {

  @Test
  void carriesPaymentAndOrderIdAndEventType() {
    PaymentCompletedEvent event =
        new PaymentCompletedEvent(1L, 100L, "alice", new BigDecimal("39.98"), "USD");

    assertThat(event.getPaymentId()).isEqualTo(1L);
    assertThat(event.getOrderId()).isEqualTo(100L);
    assertThat(event.getCustomerUsername()).isEqualTo("alice");
    assertThat(event.getAmount()).isEqualTo(new BigDecimal("39.98"));
    assertThat(event.getCurrency()).isEqualTo("USD");
    assertThat(event.getEventType()).isEqualTo("PaymentCompletedEvent");
  }
}
