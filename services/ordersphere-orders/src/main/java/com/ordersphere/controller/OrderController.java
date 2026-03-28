package com.ordersphere.controller;

import com.ordersphere.dto.CreateOrderRequest;
import com.ordersphere.service.OrderService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {


    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<String> createOrder(
            @RequestBody CreateOrderRequest request, Authentication authentication) {
        orderService.createOrder(request,
                authentication.getName());
        return ResponseEntity.ok("Order created successfully");
    }

    @GetMapping
    public String getOrders() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return "Hello " + auth.getName() + ", here are your orders!";
    }


}
