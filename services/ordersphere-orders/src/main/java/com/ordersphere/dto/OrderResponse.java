package com.ordersphere.dto;

public record OrderResponse(
        Long orderId,
        String status,
        String message
) {}
