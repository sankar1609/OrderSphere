package com.ordersphere.dummygateway;

import java.math.BigDecimal;
import java.time.Instant;

/** One payable checkout. Mutable state is guarded by the instance monitor. */
public class CheckoutSession {

  public enum Status {
    OPEN,
    SUCCEEDED,
    CANCELLED,
    EXPIRED
  }

  private final String id;
  private final String merchantReference;
  private final BigDecimal amount;
  private final String currency;
  private final String description;
  private final String successUrl;
  private final String cancelUrl;
  private final String webhookUrl;
  private final Instant expiresAt;
  private Status status = Status.OPEN;
  private String chargeReference;
  private String refundReference;

  public CheckoutSession(
      String id,
      String merchantReference,
      BigDecimal amount,
      String currency,
      String description,
      String successUrl,
      String cancelUrl,
      String webhookUrl,
      Instant expiresAt) {
    this.id = id;
    this.merchantReference = merchantReference;
    this.amount = amount;
    this.currency = currency;
    this.description = description;
    this.successUrl = successUrl;
    this.cancelUrl = cancelUrl;
    this.webhookUrl = webhookUrl;
    this.expiresAt = expiresAt;
  }

  /** Current status; an OPEN session past its expiry is reported (and stays) EXPIRED. */
  public synchronized Status status(Instant now) {
    if (status == Status.OPEN && now.isAfter(expiresAt)) {
      status = Status.EXPIRED;
    }
    return status;
  }

  /** Moves OPEN → the given final status; returns false if the session was no longer OPEN. */
  public synchronized boolean complete(Status finalStatus, String chargeReference, Instant now) {
    if (status(now) != Status.OPEN) {
      return false;
    }
    this.status = finalStatus;
    this.chargeReference = chargeReference;
    return true;
  }

  /** Records a refund once; returns the (possibly pre-existing) refund reference. */
  public synchronized String refund(String newRefundReference) {
    if (refundReference == null) {
      refundReference = newRefundReference;
    }
    return refundReference;
  }

  public String getId() {
    return id;
  }

  public String getMerchantReference() {
    return merchantReference;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getDescription() {
    return description;
  }

  public String getSuccessUrl() {
    return successUrl;
  }

  public String getCancelUrl() {
    return cancelUrl;
  }

  public String getWebhookUrl() {
    return webhookUrl;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public synchronized String getChargeReference() {
    return chargeReference;
  }
}
