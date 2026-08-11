package com.ordersphere.payment.gateway;

import java.math.BigDecimal;

public interface PaymentGatewayClient {

  GatewayResult authorize(String paymentMethodToken, BigDecimal amount, String currency);

  GatewayResult refund(String paymentMethodToken, BigDecimal amount);
}
