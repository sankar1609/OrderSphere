package com.ordersphere.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreatePaymentRequest(
    @NotNull Long orderId,
    @NotNull Long paymentMethodId,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank String currency) {}
