package com.ordersphere.orders.service;

import com.ordersphere.events.OrderCancelledEvent;
import com.ordersphere.events.OrderConfirmedEvent;
import com.ordersphere.events.OrderCreatedEvent;
import com.ordersphere.orders.client.InventoryClient;
import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderItem;
import com.ordersphere.orders.domain.OrderStatus;
import com.ordersphere.orders.dto.CreateOrderRequest;
import com.ordersphere.orders.dto.OrderResponse;
import com.ordersphere.orders.exception.InventoryReservationException;
import com.ordersphere.orders.exception.OrderNotFoundException;
import com.ordersphere.orders.repository.OrderRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private final OrderRepository orderRepository;
  private final InventoryClient inventoryClient;
  private final ApplicationEventPublisher eventPublisher;

  public OrderService(
      OrderRepository orderRepository,
      InventoryClient inventoryClient,
      ApplicationEventPublisher eventPublisher) {
    this.orderRepository = orderRepository;
    this.inventoryClient = inventoryClient;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public OrderResponse createOrder(
      String username, CreateOrderRequest request, String bearerToken) {
    Order order = new Order(username);
    for (CreateOrderRequest.Item item : request.items()) {
      order.addItem(new OrderItem(item.sku(), item.quantity()));
    }
    orderRepository.save(order);

    eventPublisher.publishEvent(
        new OrderCreatedEvent(order.getId(), username, quantitiesBySku(request)));

    List<InventoryClient.ReserveRequest.Item> reserveItems =
        request.items().stream()
            .map(item -> new InventoryClient.ReserveRequest.Item(item.sku(), item.quantity()))
            .toList();

    try {
      inventoryClient.reserve(order.getId(), reserveItems, bearerToken);
      order.markStatus(OrderStatus.CONFIRMED);
      orderRepository.save(order);
      eventPublisher.publishEvent(new OrderConfirmedEvent(order.getId()));
    } catch (InventoryReservationException ex) {
      order.markStatus(OrderStatus.CANCELLED);
      orderRepository.save(order);
      eventPublisher.publishEvent(
          new OrderCancelledEvent(order.getId(), OrderCancelledEvent.Reason.INVENTORY_UNAVAILABLE));
    }

    return OrderResponse.from(order);
  }

  @Transactional(readOnly = true)
  public List<OrderResponse> listOrders(String username) {
    return orderRepository.findByCustomerUsername(username).stream()
        .map(OrderResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public OrderResponse getOrder(String username, boolean isAdmin, Long orderId) {
    return OrderResponse.from(findOrderOrThrow(username, isAdmin, orderId));
  }

  @Transactional
  public OrderResponse cancelOrder(
      String username, boolean isAdmin, Long orderId, String bearerToken) {
    Order order = findOrderOrThrow(username, isAdmin, orderId);

    if (order.getStatus() == OrderStatus.CANCELLED) {
      return OrderResponse.from(order);
    }
    if (order.getStatus() == OrderStatus.CONFIRMED) {
      inventoryClient.release(order.getId(), bearerToken);
    }

    order.markStatus(OrderStatus.CANCELLED);
    orderRepository.save(order);
    eventPublisher.publishEvent(
        new OrderCancelledEvent(order.getId(), OrderCancelledEvent.Reason.CUSTOMER_REQUESTED));

    return OrderResponse.from(order);
  }

  private Order findOrderOrThrow(String username, boolean isAdmin, Long orderId) {
    if (isAdmin) {
      return orderRepository
          .findById(orderId)
          .orElseThrow(() -> new OrderNotFoundException(orderId));
    }
    return orderRepository
        .findByIdAndCustomerUsername(orderId, username)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  private Map<String, Integer> quantitiesBySku(CreateOrderRequest request) {
    Map<String, Integer> quantities = new HashMap<>();
    for (CreateOrderRequest.Item item : request.items()) {
      quantities.merge(item.sku(), item.quantity(), Integer::sum);
    }
    return quantities;
  }
}
