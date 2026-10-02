package com.ordersphere.orders.controller;

import com.ordersphere.orders.dto.UnshippedOrderResponse;
import com.ordersphere.orders.service.OrderShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin view of paid orders that have no shipment yet. Ones with {@code retrying: false} were
 * rejected by shipping or ran out of retries and need a person: fix the cause, then retry.
 */
@Tag(name = "Unshipped orders (admin)", description = "Paid orders without a shipment, with retry")
@RestController
@RequestMapping("/orders/admin/unshipped")
@PreAuthorize("hasRole('ADMIN')")
public class UnshippedOrderAdminController {

  private final OrderShipmentService orderShipmentService;

  public UnshippedOrderAdminController(OrderShipmentService orderShipmentService) {
    this.orderShipmentService = orderShipmentService;
  }

  @GetMapping
  public List<UnshippedOrderResponse> list() {
    return orderShipmentService.listUnshipped();
  }

  /** Tries to create the shipment now, with a fresh retry budget if it fails again. */
  @Operation(
      summary = "Retry creating the shipment",
      description =
          "Tries now with a fresh retry budget; 409 if the order isn't CONFIRMED or already has a shipment.")
  @PostMapping("/{orderId}/retry")
  public UnshippedOrderResponse retry(@PathVariable Long orderId) {
    return orderShipmentService.retry(orderId);
  }
}
