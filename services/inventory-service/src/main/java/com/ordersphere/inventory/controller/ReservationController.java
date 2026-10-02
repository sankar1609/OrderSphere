package com.ordersphere.inventory.controller;

import com.ordersphere.inventory.dto.ReservationResponse;
import com.ordersphere.inventory.dto.ReserveStockRequest;
import com.ordersphere.inventory.service.ReservationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal: stock reservations are driven by the ordersphere-orders saga, never by customers
 * directly - otherwise anyone could hold stock under fake order ids or release a paid order's
 * reservation.
 */
@Tag(name = "Reservations (internal)", description = "Stock holds driven by the orders saga")
@RestController
@RequestMapping("/inventory/reservations")
@PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
public class ReservationController {

  private final ReservationService reservationService;

  public ReservationController(ReservationService reservationService) {
    this.reservationService = reservationService;
  }

  @PostMapping
  public ResponseEntity<ReservationResponse> reserve(
      @Valid @RequestBody ReserveStockRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.reserve(request));
  }

  @PostMapping("/{orderId}/confirm")
  public ReservationResponse confirm(@PathVariable Long orderId) {
    return reservationService.confirm(orderId);
  }

  @PostMapping("/{orderId}/release")
  public ReservationResponse release(@PathVariable Long orderId) {
    return reservationService.release(orderId);
  }
}
