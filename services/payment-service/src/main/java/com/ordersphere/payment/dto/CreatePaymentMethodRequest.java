package com.ordersphere.payment.dto;

import com.ordersphere.payment.domain.PaymentMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePaymentMethodRequest(@NotNull PaymentMethodType type, @NotBlank String token) {}
