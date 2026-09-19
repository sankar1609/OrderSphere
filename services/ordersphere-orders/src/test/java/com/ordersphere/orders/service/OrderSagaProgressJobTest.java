package com.ordersphere.orders.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderStatus;
import com.ordersphere.orders.repository.OrderRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderSagaProgressJobTest {

  @Mock private OrderRepository orderRepository;
  @Mock private OrderService orderService;

  @Test
  void progressAwaitingPaymentOrdersDelegatesEachAwaitingPaymentOrder() {
    Order order = new Order("alice");
    order.setId(10L);
    order.markStatus(OrderStatus.AWAITING_PAYMENT);
    when(orderRepository.findByStatus(OrderStatus.AWAITING_PAYMENT)).thenReturn(List.of(order));

    OrderSagaProgressJob job = new OrderSagaProgressJob(orderRepository, orderService);
    job.progressAwaitingPaymentOrders();

    verify(orderService).progressAwaitingPayment(10L);
  }

  @Test
  void progressAwaitingPaymentOrdersDoesNothingWhenNonePending() {
    when(orderRepository.findByStatus(OrderStatus.AWAITING_PAYMENT)).thenReturn(List.of());

    OrderSagaProgressJob job = new OrderSagaProgressJob(orderRepository, orderService);
    job.progressAwaitingPaymentOrders();

    verifyNoInteractions(orderService);
  }
}
