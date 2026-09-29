package com.ordersphere.orders.service;

import com.ordersphere.orders.domain.OrderStatus;
import com.ordersphere.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderSagaProgressJob {

  private static final Logger log = LoggerFactory.getLogger(OrderSagaProgressJob.class);

  private final OrderRepository orderRepository;
  private final OrderService orderService;

  public OrderSagaProgressJob(OrderRepository orderRepository, OrderService orderService) {
    this.orderRepository = orderRepository;
    this.orderService = orderService;
  }

  @Scheduled(fixedDelayString = "${orders.saga.sweep-interval-ms}")
  public void sweep() {
    progressAwaitingPaymentOrders();
  }

  public void progressAwaitingPaymentOrders() {
    for (var order : orderRepository.findByStatus(OrderStatus.AWAITING_PAYMENT)) {
      // One order's failure (e.g. auth-service briefly unreachable) mustn't stop the rest of the
      // sweep; the order is simply retried on the next one.
      try {
        orderService.progressAwaitingPayment(order.getId());
      } catch (RuntimeException ex) {
        log.warn("Could not progress orderId {} this sweep: {}", order.getId(), ex.getMessage());
      }
    }
  }
}
