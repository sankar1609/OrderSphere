package com.ordersphere.orders.controller;

import com.ordersphere.orders.dto.CreateOrderRequest;
import com.ordersphere.orders.dto.OrderResponse;
import com.ordersphere.orders.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping
  public ResponseEntity<OrderResponse> createOrder(
      @Valid @RequestBody CreateOrderRequest request,
      Principal principal,
      HttpServletRequest httpRequest) {
    OrderResponse response =
        orderService.createOrder(principal.getName(), request, bearerToken(httpRequest));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  public List<OrderResponse> listOrders(Principal principal) {
    return orderService.listOrders(principal.getName());
  }

  @GetMapping("/{orderId}")
  public OrderResponse getOrder(@PathVariable Long orderId, Authentication authentication) {
    return orderService.getOrder(authentication.getName(), isAdmin(authentication), orderId);
  }

  @PostMapping("/{orderId}/cancel")
  public OrderResponse cancelOrder(
      @PathVariable Long orderId, Authentication authentication, HttpServletRequest httpRequest) {
    return orderService.cancelOrder(
        authentication.getName(), isAdmin(authentication), orderId, bearerToken(httpRequest));
  }

  private boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  private String bearerToken(HttpServletRequest request) {
    return request.getHeader(HttpHeaders.AUTHORIZATION);
  }
}
