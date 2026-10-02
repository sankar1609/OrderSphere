package com.ordersphere.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReserveStockRequest(
    @NotNull Long orderId,
    @NotEmpty @Size(max = InventoryLimits.MAX_LINES) @Valid List<Item> items) {

  public record Item(
      @NotBlank String sku, @Min(1) @Max(InventoryLimits.MAX_LINE_QUANTITY) int quantity) {}
}
