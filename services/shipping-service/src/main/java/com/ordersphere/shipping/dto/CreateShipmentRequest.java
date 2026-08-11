package com.ordersphere.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateShipmentRequest(@NotNull Long orderId, @NotBlank String destination) {}
