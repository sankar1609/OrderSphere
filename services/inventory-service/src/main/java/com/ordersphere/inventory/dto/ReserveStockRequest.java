package com.ordersphere.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReserveStockRequest(@NotNull Long orderId, @NotEmpty @Valid List<Item> items) {

  public record Item(@NotBlank String sku, @Min(1) int quantity) {}
}
