package com.ordersphere.shipping.dto;

import jakarta.validation.constraints.NotBlank;

public record ReturnShipmentRequest(@NotBlank String reason) {}
