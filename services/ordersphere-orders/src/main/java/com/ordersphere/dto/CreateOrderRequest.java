package com.ordersphere.dto;

public record CreateOrderRequest(
        Long productId,
        int quantity
) {}
