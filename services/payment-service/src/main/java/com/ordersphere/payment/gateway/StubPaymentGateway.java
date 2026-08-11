package com.ordersphere.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StubPaymentGateway implements PaymentGatewayClient {

  public static final String DECLINE_TOKEN = "FAIL-DECLINE";
  public static final String TRANSIENT_FAILURE_TOKEN = "FAIL-TRANSIENT";

  @Override
  public GatewayResult authorize(String paymentMethodToken, BigDecimal amount, String currency) {
    return simulate(paymentMethodToken);
  }

  @Override
  public GatewayResult refund(String paymentMethodToken, BigDecimal amount) {
    return simulate(paymentMethodToken);
  }

  private GatewayResult simulate(String paymentMethodToken) {
    if (DECLINE_TOKEN.equals(paymentMethodToken)) {
      return new GatewayResult(GatewayResult.GatewayOutcome.DECLINED, null);
    }
    if (TRANSIENT_FAILURE_TOKEN.equals(paymentMethodToken)) {
      return new GatewayResult(GatewayResult.GatewayOutcome.TRANSIENT_FAILURE, null);
    }
    return new GatewayResult(GatewayResult.GatewayOutcome.SUCCESS, "gw-" + UUID.randomUUID());
  }
}
