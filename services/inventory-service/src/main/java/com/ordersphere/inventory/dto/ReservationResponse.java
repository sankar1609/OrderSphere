package com.ordersphere.inventory.dto;

import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.domain.Reservation;
import com.ordersphere.inventory.domain.ReservationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReservationResponse(
    Long orderId, ReservationStatus status, Instant expiresAt, List<LineItem> reserved) {

  /** {@code unitPrice} is the product's current price, so callers can price the order. */
  public record LineItem(String sku, int quantity, BigDecimal unitPrice) {}

  public static ReservationResponse from(Reservation reservation) {
    return new ReservationResponse(
        reservation.getOrderId(),
        reservation.getStatus(),
        reservation.getExpiresAt(),
        reservation.getItems().stream()
            .map(item -> lineItem(item.getProduct(), item.getQuantityReserved()))
            .toList());
  }

  private static LineItem lineItem(Product product, int quantity) {
    return new LineItem(product.getSku(), quantity, product.getUnitPrice());
  }
}
