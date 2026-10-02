package com.ordersphere.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Service-to-service request (ordersphere-orders initiating payment for an order it created), so
 * the customer is named explicitly rather than taken from the caller's token.
 */
public record CreatePaymentRequest(
    @NotNull Long orderId,
    @NotBlank String customerUsername,
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank String currency) {}
