package com.ordersphere.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateOrderRequest(
    @NotEmpty @Valid List<Item> items,
    @NotNull Long paymentMethodId,
    @NotBlank String currency,
    @NotBlank String shippingDestination) {

  public record Item(@NotBlank String sku, @Min(1) int quantity) {}
}
