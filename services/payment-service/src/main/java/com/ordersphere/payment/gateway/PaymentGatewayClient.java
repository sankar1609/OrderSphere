package com.ordersphere.payment.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

  /** The provider's settlement report: every charge and refund in [from, to). */
  List<ProviderTransaction> transactions(Instant from, Instant to);

  /** {@code refundReference} is set once the provider has refunded the charge. */
  record CheckoutSession(
      String id, String url, SessionStatus status, String chargeReference, String refundReference) {

    public CheckoutSession(String id, String url, SessionStatus status, String chargeReference) {
      this(id, url, status, chargeReference, null);
    }
  }

  /** One line of the settlement report. */
  record ProviderTransaction(
      Type type,
      String sessionId,
      String chargeReference,
      String refundReference,
      BigDecimal amount,
      String currency,
      Instant occurredAt) {

    public enum Type {
      CHARGE,
      REFUND
    }
  }

  enum SessionStatus {
    OPEN,
    SUCCEEDED,
    CANCELLED,
    EXPIRED
  }
}
