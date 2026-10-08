package com.ordersphere.orders.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.ordersphere.events.PaymentCompletedEvent;
import com.ordersphere.events.PaymentFailedEvent;
import com.ordersphere.orders.logging.OrderLogContext;
import com.ordersphere.orders.service.OrderService;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

  @Mock private OrderService orderService;

  @Test
  void onPaymentCompletedDelegatesAsSucceeded() {
    PaymentEventListener listener = new PaymentEventListener(orderService);

    listener.onPaymentCompleted(
        new PaymentCompletedEvent(1L, 10L, "alice", new BigDecimal("39.98"), "USD"));

    verify(orderService).onPaymentEvent(10L, true);
  }

  @Test
  void onPaymentFailedDelegatesAsNotSucceeded() {
    PaymentEventListener listener = new PaymentEventListener(orderService);

    listener.onPaymentFailed(new PaymentFailedEvent(1L, 10L, "declined", "alice"));

    verify(orderService).onPaymentEvent(10L, false);
  }

  @Test
  void handlesTheEventWithTheOrderIdInTheLogContext() {
    AtomicReference<String> orderIdWhileHandling = new AtomicReference<>();
    doAnswer(
            invocation -> {
              orderIdWhileHandling.set(MDC.get(OrderLogContext.ORDER_ID));
              return null;
            })
        .when(orderService)
        .onPaymentEvent(anyLong(), anyBoolean());

    new PaymentEventListener(orderService)
        .onPaymentCompleted(
            new PaymentCompletedEvent(1L, 10L, "alice", new BigDecimal("39.98"), "USD"));

    assertThat(orderIdWhileHandling.get()).isEqualTo("10");
    assertThat(MDC.get(OrderLogContext.ORDER_ID)).isNull();
  }
}
