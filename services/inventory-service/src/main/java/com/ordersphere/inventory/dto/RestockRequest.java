package com.ordersphere.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RestockRequest(@Min(1) @Max(InventoryLimits.MAX_STOCK_CHANGE) int quantity) {}
