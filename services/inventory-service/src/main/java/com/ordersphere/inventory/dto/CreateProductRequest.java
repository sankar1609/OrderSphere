package com.ordersphere.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
    @NotBlank
        @Pattern(
            regexp = InventoryLimits.SKU_PATTERN,
            message = "must be 1-64 letters, digits, '.', '_' or '-'")
        String sku,
    @NotBlank @Size(max = InventoryLimits.MAX_NAME_LENGTH) String name,
    @Min(0) @Max(InventoryLimits.MAX_STOCK_CHANGE) int quantityOnHand,
    @Min(0) @Max(InventoryLimits.MAX_STOCK) int reorderThreshold,
    @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2)
        BigDecimal unitPrice) {}
