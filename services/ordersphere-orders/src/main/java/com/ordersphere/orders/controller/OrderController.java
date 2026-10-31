package com.ordersphere.orders.controller;

import com.ordersphere.orders.dto.CreateOrderRequest;
import com.ordersphere.orders.dto.OrderResponse;
import com.ordersphere.orders.dto.PageResponse;
import com.ordersphere.orders.logging.OrderLogContext;
import com.ordersphere.orders.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders", description = "Place, view and cancel orders")
@Validated
@RestController
@RequestMapping("/orders")
public class OrderController {

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @Operation(
      summary = "Place an order",
      description =
          "Reserves stock (all lines or none) and opens a hosted checkout; pay at the returned checkoutUrl. An order that can't be filled comes back CANCELLED with a cancellationReason.")
  @PostMapping
  public ResponseEntity<OrderResponse> createOrder(
      @Valid @RequestBody CreateOrderRequest request, Principal principal) {
    OrderResponse response = orderService.createOrder(principal.getName(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(summary = "The caller's orders, newest first, a page at a time")
  @GetMapping
  public PageResponse<OrderResponse> listOrders(
      Principal principal,
      @RequestParam(defaultValue = "0") @Min(0) @Max(10_000_000) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
    return orderService.listOrders(principal.getName(), page, size);
  }

  @GetMapping("/{orderId}")
  public OrderResponse getOrder(@PathVariable Long orderId, Authentication authentication) {
    return orderService.getOrder(authentication.getName(), isAdmin(authentication), orderId);
  }

  @Operation(
      summary = "Cancel an order",
      description =
          "Idempotent. Releases the stock, refunds a paid order and cancels an undelivered shipment; 409 once delivered.")
  @PostMapping("/{orderId}/cancel")
  public OrderResponse cancelOrder(@PathVariable Long orderId, Authentication authentication) {
    try (var logContext = OrderLogContext.forOrder(orderId)) {
      return orderService.cancelOrder(authentication.getName(), isAdmin(authentication), orderId);
    }
  }

  private boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }
}
