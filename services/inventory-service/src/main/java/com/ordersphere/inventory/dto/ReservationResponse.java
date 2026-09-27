package com.ordersphere.inventory.dto;

import com.ordersphere.inventory.domain.Backorder;
import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.domain.Reservation;
import com.ordersphere.inventory.domain.ReservationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ReservationResponse(
    Long orderId,
    ReservationStatus status,
    Instant expiresAt,
    List<LineItem> reserved,
    List<LineItem> backordered) {

  /** {@code unitPrice} is the product's current price, so callers can price the order. */
  public record LineItem(String sku, int quantity, BigDecimal unitPrice) {}

  public static ReservationResponse of(Reservation reservation, List<Backorder> backorders) {
    List<LineItem> reservedItems =
        reservation.getItems().stream()
            .map(item -> lineItem(item.getProduct(), item.getQuantityReserved()))
            .toList();

    Map<String, LineItem> backorderedBySku = new LinkedHashMap<>();
    for (Backorder backorder : backorders) {
      Product product = backorder.getProduct();
      backorderedBySku.merge(
          product.getSku(),
          lineItem(product, backorder.getQuantity()),
          (a, b) -> new LineItem(a.sku(), a.quantity() + b.quantity(), a.unitPrice()));
    }
    List<LineItem> backorderedItems =
        backorderedBySku.values().stream().filter(item -> item.quantity() > 0).toList();

    return new ReservationResponse(
        reservation.getOrderId(),
        reservation.getStatus(),
        reservation.getExpiresAt(),
        reservedItems,
        backorderedItems);
  }

  private static LineItem lineItem(Product product, int quantity) {
    return new LineItem(product.getSku(), quantity, product.getUnitPrice());
  }
}
