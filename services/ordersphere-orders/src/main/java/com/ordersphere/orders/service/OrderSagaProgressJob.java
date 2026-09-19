package com.ordersphere.orders.service;

import com.ordersphere.orders.domain.OrderStatus;
import com.ordersphere.orders.repository.OrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderSagaProgressJob {

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
    orderRepository
        .findByStatus(OrderStatus.AWAITING_PAYMENT)
        .forEach(order -> orderService.progressAwaitingPayment(order.getId()));
  }
}
