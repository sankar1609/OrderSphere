package com.ordersphere.orders.dto;

import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderStatus;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    Long id,
    String customerUsername,
    OrderStatus status,
    List<LineItem> items,
    Instant createdAt,
    Instant updatedAt) {

  public record LineItem(String sku, int quantity) {}

  public static OrderResponse from(Order order) {
    List<LineItem> lineItems =
        order.getItems().stream()
            .map(item -> new LineItem(item.getSku(), item.getQuantity()))
            .toList();

    return new OrderResponse(
        order.getId(),
        order.getCustomerUsername(),
        order.getStatus(),
        lineItems,
        order.getCreatedAt(),
        order.getUpdatedAt());
  }
}
