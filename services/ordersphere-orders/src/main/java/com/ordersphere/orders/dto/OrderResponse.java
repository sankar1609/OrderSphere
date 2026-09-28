package com.ordersphere.orders.dto;

import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    Long id,
    String customerUsername,
    OrderStatus status,
    List<LineItem> items,
    Long paymentId,
    String checkoutUrl,
    Long shipmentId,
    String shippingDestination,
    BigDecimal totalAmount,
    String currency,
    Instant createdAt,
    Instant updatedAt) {

  public record LineItem(String sku, int quantity, BigDecimal unitPrice) {}

  public static OrderResponse from(Order order) {
    List<LineItem> lineItems =
        order.getItems().stream()
            .map(item -> new LineItem(item.getSku(), item.getQuantity(), item.getUnitPrice()))
            .toList();

    return new OrderResponse(
        order.getId(),
        order.getCustomerUsername(),
        order.getStatus(),
        lineItems,
        order.getPaymentId(),
        order.getStatus() == OrderStatus.AWAITING_PAYMENT ? order.getCheckoutUrl() : null,
        order.getShipmentId(),
        order.getShippingDestination(),
        order.getTotalAmount(),
        order.getCurrency(),
        order.getCreatedAt(),
        order.getUpdatedAt());
  }
}
