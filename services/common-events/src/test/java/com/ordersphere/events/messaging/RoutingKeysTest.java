package com.ordersphere.events.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoutingKeysTest {

  @Test
  void convertsSimpleEventTypeToDotCase() {
    assertThat(RoutingKeys.forEventType("OrderConfirmedEvent")).isEqualTo("order.confirmed");
    assertThat(RoutingKeys.forEventType("PaymentCompletedEvent")).isEqualTo("payment.completed");
    assertThat(RoutingKeys.forEventType("PaymentFailedEvent")).isEqualTo("payment.failed");
  }

  @Test
  void handlesEventTypesWithoutTheEventSuffix() {
    assertThat(RoutingKeys.forEventType("StockLow")).isEqualTo("stock.low");
  }

  @Test
  void handlesSingleWordEventTypes() {
    assertThat(RoutingKeys.forEventType("Event")).isEmpty();
  }
}
