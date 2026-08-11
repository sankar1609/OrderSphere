package com.ordersphere.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
    @NotEmpty @Valid List<Item> items,
    @NotNull Long paymentMethodId,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank String currency,
    @NotBlank String shippingDestination) {

  public record Item(@NotBlank String sku, @Min(1) int quantity) {}
}
