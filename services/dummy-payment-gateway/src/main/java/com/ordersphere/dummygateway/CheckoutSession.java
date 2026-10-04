package com.ordersphere.dummygateway;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One payable checkout, stored in gateway_db. Changes happen under a row lock (see {@link
 * CheckoutSessionRepository#findByIdForUpdate}), so a pay and a cancel can't both win.
 */
@Entity
@Table(name = "checkout_sessions")
public class CheckoutSession {

  public enum Status {
    OPEN,
    SUCCEEDED,
    CANCELLED,
    EXPIRED
  }

  @Id private String id;

  @Column(name = "merchant_reference", nullable = false)
  private String merchantReference;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false)
  private String currency;

  private String description;

  @Column(name = "success_url")
  private String successUrl;

  @Column(name = "cancel_url")
  private String cancelUrl;

  @Column(name = "webhook_url")
  private String webhookUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.OPEN;

  @Column(name = "charge_reference")
  private String chargeReference;

  @Column(name = "refund_reference")
  private String refundReference;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "charged_at")
  private Instant chargedAt;

  @Column(name = "refunded_at")
  private Instant refundedAt;

  protected CheckoutSession() {}

  public CheckoutSession(
      String id,
      String merchantReference,
      BigDecimal amount,
      String currency,
      String description,
      String successUrl,
      String cancelUrl,
      String webhookUrl,
      Instant createdAt,
      Instant expiresAt) {
    this.id = id;
    this.merchantReference = merchantReference;
    this.amount = amount;
    this.currency = currency;
    this.description = description;
    this.successUrl = successUrl;
    this.cancelUrl = cancelUrl;
    this.webhookUrl = webhookUrl;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  /** Current status; an OPEN session past its expiry is reported (and stays) EXPIRED. */
  public Status status(Instant now) {
    if (status == Status.OPEN && now.isAfter(expiresAt)) {
      status = Status.EXPIRED;
    }
    return status;
  }

  /** Moves OPEN → the given final status; returns false if the session was no longer OPEN. */
  public boolean complete(Status finalStatus, String chargeReference, Instant now) {
    if (status(now) != Status.OPEN) {
      return false;
    }
    this.status = finalStatus;
    this.chargeReference = chargeReference;
    if (finalStatus == Status.SUCCEEDED) {
      this.chargedAt = now;
    }
    return true;
  }

  /** Records a refund once; returns the (possibly pre-existing) refund reference. */
  public String refund(String newRefundReference, Instant now) {
    if (refundReference == null) {
      refundReference = newRefundReference;
      refundedAt = now;
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public String getChargeReference() {
    return chargeReference;
  }

  public String getRefundReference() {
    return refundReference;
  }

  public Instant getChargedAt() {
    return chargedAt;
  }

  public Instant getRefundedAt() {
    return refundedAt;
  }
}
