package com.ordersphere.inventory.dto;

import jakarta.validation.constraints.Min;

public record RestockRequest(@Min(1) int quantity) {}
