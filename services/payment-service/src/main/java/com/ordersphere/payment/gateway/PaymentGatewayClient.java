package com.ordersphere.payment.gateway;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * A hosted-checkout payment provider: the customer pays on the provider's own page, so card details
 * never reach OrderSphere. The provider reports the outcome by webhook and by session lookup.
 */
public interface PaymentGatewayClient {

  /** Opens a checkout session the customer is redirected to. */
  CheckoutSession createCheckoutSession(CheckoutRequest request);

  /** The session's current state, or empty if the provider doesn't know it. */
  Optional<CheckoutSession> getCheckoutSession(String sessionId);

  /** Refunds a charge in full; returns the refund reference, or empty if the charge is unknown. */
  Optional<String> refund(String chargeReference);

  record CheckoutRequest(
      String merchantReference,
      BigDecimal amount,
      String currency,
      String description,
      String successUrl,
      String cancelUrl,
      String webhookUrl) {}

  record CheckoutSession(String id, String url, SessionStatus status, String chargeReference) {}

  enum SessionStatus {
    OPEN,
    SUCCEEDED,
    CANCELLED,
    EXPIRED
  }
}
