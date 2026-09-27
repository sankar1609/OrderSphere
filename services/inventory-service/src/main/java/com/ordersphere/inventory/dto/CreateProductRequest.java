package com.ordersphere.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateProductRequest(
    @NotBlank String sku,
    @NotBlank String name,
    @Min(0) int quantityOnHand,
    @Min(0) int reorderThreshold,
    @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2)
        BigDecimal unitPrice) {}
