package com.ordersphere.orders.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Validated up front so a bad request is a 400 before anything is reserved or charged. The limits
 * match inventory-service's (an order line of at most 10,000 units, at most 50 lines) and the
 * database columns (SKU 64, destination 255).
 */
public record CreateOrderRequest(
    @NotEmpty @Size(max = 50) @Valid List<Item> items,
    @NotBlank
        @IsoCurrency
        @Schema(
            description = "ISO 4217 currency code (upper case)",
            pattern = "[A-Z]{3}",
            example = "USD")
        String currency,
    @NotBlank @Size(max = 255) String shippingDestination) {

  public record Item(@NotBlank @Size(max = 64) String sku, @Min(1) @Max(10_000) int quantity) {}
}
