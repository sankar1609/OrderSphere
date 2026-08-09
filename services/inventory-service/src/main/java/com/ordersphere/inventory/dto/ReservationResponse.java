package com.ordersphere.inventory.dto;

import com.ordersphere.inventory.domain.Reservation;
import com.ordersphere.inventory.domain.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ReservationResponse(
    Long orderId,
    ReservationStatus status,
    Instant expiresAt,
    List<LineItem> reserved,
    List<LineItem> backordered) {

  public record LineItem(String sku, int quantity) {}

  public static ReservationResponse of(Reservation reservation, Map<String, Integer> backordered) {
    List<LineItem> reservedItems =
        reservation.getItems().stream()
            .map(item -> new LineItem(item.getProduct().getSku(), item.getQuantityReserved()))
            .toList();
    List<LineItem> backorderedItems =
        backordered.entrySet().stream()
            .filter(entry -> entry.getValue() > 0)
            .map(entry -> new LineItem(entry.getKey(), entry.getValue()))
            .toList();

    return new ReservationResponse(
        reservation.getOrderId(),
        reservation.getStatus(),
        reservation.getExpiresAt(),
        reservedItems,
        backorderedItems);
  }
}
