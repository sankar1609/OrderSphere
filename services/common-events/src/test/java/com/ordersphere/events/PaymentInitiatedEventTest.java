package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentInitiatedEventTest {

  @Test
  void carriesPaymentFieldsAndEventType() {
    PaymentInitiatedEvent event =
        new PaymentInitiatedEvent(1L, 100L, new BigDecimal("49.99"), "USD");

    assertThat(event.getPaymentId()).isEqualTo(1L);
    assertThat(event.getOrderId()).isEqualTo(100L);
    assertThat(event.getAmount()).isEqualTo(new BigDecimal("49.99"));
    assertThat(event.getCurrency()).isEqualTo("USD");
    assertThat(event.getEventType()).isEqualTo("PaymentInitiatedEvent");
  }
}
