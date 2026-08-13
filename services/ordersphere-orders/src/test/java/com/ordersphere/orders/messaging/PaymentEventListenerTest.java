package com.ordersphere.orders.messaging;

import static org.mockito.Mockito.verify;

import com.ordersphere.events.PaymentCompletedEvent;
import com.ordersphere.events.PaymentFailedEvent;
import com.ordersphere.orders.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

  @Mock private OrderService orderService;

  @Test
  void onPaymentCompletedDelegatesAsSucceeded() {
    PaymentEventListener listener = new PaymentEventListener(orderService);

    listener.onPaymentCompleted(new PaymentCompletedEvent(1L, 10L, "alice"));

    verify(orderService).onPaymentEvent(10L, true);
  }

  @Test
  void onPaymentFailedDelegatesAsNotSucceeded() {
    PaymentEventListener listener = new PaymentEventListener(orderService);

    listener.onPaymentFailed(new PaymentFailedEvent(1L, 10L, "declined", "alice"));

    verify(orderService).onPaymentEvent(10L, false);
  }
}
