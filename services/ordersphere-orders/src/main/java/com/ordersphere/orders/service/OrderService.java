package com.ordersphere.orders.service;

import com.ordersphere.events.OrderCancelledEvent;
import com.ordersphere.events.OrderConfirmedEvent;
import com.ordersphere.events.OrderCreatedEvent;
import com.ordersphere.orders.client.InventoryClient;
import com.ordersphere.orders.client.NotificationClient;
import com.ordersphere.orders.client.PaymentClient;
import com.ordersphere.orders.client.ServiceTokenProvider;
import com.ordersphere.orders.client.ShippingClient;
import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderItem;
import com.ordersphere.orders.domain.OrderStatus;
import com.ordersphere.orders.dto.CreateOrderRequest;
import com.ordersphere.orders.dto.OrderResponse;
import com.ordersphere.orders.exception.InventoryReservationException;
import com.ordersphere.orders.exception.OrderNotFoundException;
import com.ordersphere.orders.exception.PaymentInitiationException;
import com.ordersphere.orders.exception.ShipmentCreationException;
import com.ordersphere.orders.repository.OrderRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);

  private final OrderRepository orderRepository;
  private final InventoryClient inventoryClient;
  private final PaymentClient paymentClient;
  private final ShippingClient shippingClient;
  private final NotificationClient notificationClient;
  private final ServiceTokenProvider serviceTokenProvider;
  private final ApplicationEventPublisher eventPublisher;

  public OrderService(
      OrderRepository orderRepository,
      InventoryClient inventoryClient,
      PaymentClient paymentClient,
      ShippingClient shippingClient,
      NotificationClient notificationClient,
      ServiceTokenProvider serviceTokenProvider,
      ApplicationEventPublisher eventPublisher) {
    this.orderRepository = orderRepository;
    this.inventoryClient = inventoryClient;
    this.paymentClient = paymentClient;
    this.shippingClient = shippingClient;
    this.notificationClient = notificationClient;
    this.serviceTokenProvider = serviceTokenProvider;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public OrderResponse createOrder(
      String username, CreateOrderRequest request, String bearerToken) {
    Order order = new Order(username);
    order.setShippingDestination(request.shippingDestination());
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
    } catch (InventoryReservationException ex) {
      cancelWithReason(order, OrderCancelledEvent.Reason.INVENTORY_UNAVAILABLE);
      return OrderResponse.from(order);
    }

    try {
      Long paymentId =
          paymentClient.initiate(
              order.getId(),
              request.paymentMethodId(),
              request.amount(),
              request.currency(),
              bearerToken);
      order.setPaymentId(paymentId);
      order.markStatus(OrderStatus.AWAITING_PAYMENT);
      orderRepository.save(order);
    } catch (PaymentInitiationException ex) {
      inventoryClient.release(order.getId(), bearerToken);
      cancelWithReason(order, OrderCancelledEvent.Reason.PAYMENT_FAILED);
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
    if (order.getStatus() == OrderStatus.AWAITING_PAYMENT
        || order.getStatus() == OrderStatus.CONFIRMED) {
      inventoryClient.release(order.getId(), bearerToken);
      if (order.getStatus() == OrderStatus.CONFIRMED) {
        paymentClient.refund(order.getPaymentId(), "Order cancelled by customer", bearerToken);
      }
    }

    cancelWithReason(order, OrderCancelledEvent.Reason.CUSTOMER_REQUESTED);

    return OrderResponse.from(order);
  }

  @Transactional
  public void progressAwaitingPayment(Order order) {
    if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
      return;
    }

    String serviceToken = serviceTokenProvider.bearerToken();
    PaymentClient.PaymentStatus paymentStatus =
        paymentClient.getStatus(order.getPaymentId(), serviceToken);

    if (paymentStatus == PaymentClient.PaymentStatus.COMPLETED) {
      try {
        Long shipmentId =
            shippingClient.createShipment(
                order.getId(), order.getShippingDestination(), serviceToken);
        order.setShipmentId(shipmentId);
      } catch (ShipmentCreationException ex) {
        log.warn("Shipment creation failed for orderId {}: {}", order.getId(), ex.getMessage());
      }
      order.markStatus(OrderStatus.CONFIRMED);
      orderRepository.save(order);
      eventPublisher.publishEvent(new OrderConfirmedEvent(order.getId()));
      notificationClient.notify(
          order.getCustomerUsername(),
          NotificationClient.TemplateKey.ORDER_CONFIRMED,
          Map.of("orderId", order.getId().toString()),
          serviceTokenProvider.bearerToken());
    } else if (paymentStatus == PaymentClient.PaymentStatus.FAILED) {
      inventoryClient.release(order.getId(), serviceToken);
      cancelWithReason(order, OrderCancelledEvent.Reason.PAYMENT_FAILED);
    }
  }

  private void cancelWithReason(Order order, OrderCancelledEvent.Reason reason) {
    order.markStatus(OrderStatus.CANCELLED);
    orderRepository.save(order);
    eventPublisher.publishEvent(new OrderCancelledEvent(order.getId(), reason));
    notificationClient.notify(
        order.getCustomerUsername(),
        NotificationClient.TemplateKey.ORDER_CANCELLED,
        Map.of("orderId", order.getId().toString()),
        serviceTokenProvider.bearerToken());
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
