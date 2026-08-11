package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RefundIssuedEventTest {

  @Test
  void carriesRefundFieldsAndEventType() {
    RefundIssuedEvent event = new RefundIssuedEvent(1L, 2L, 100L, new BigDecimal("49.99"));

    assertThat(event.getPaymentId()).isEqualTo(1L);
    assertThat(event.getRefundId()).isEqualTo(2L);
    assertThat(event.getOrderId()).isEqualTo(100L);
    assertThat(event.getAmount()).isEqualTo(new BigDecimal("49.99"));
    assertThat(event.getEventType()).isEqualTo("RefundIssuedEvent");
  }
}
