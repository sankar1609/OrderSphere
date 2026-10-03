package com.ordersphere.payment.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One place where our records and the provider's disagree. */
@Entity
@Table(name = "reconciliation_findings")
@Getter
@Setter
@NoArgsConstructor
public class ReconciliationFinding {

  public enum Type {
    /** The provider charged the card; our payment isn't COMPLETED/REFUNDED. Re-syncable. */
    CHARGED_NOT_RECORDED(true),
    /** Our payment is COMPLETED/REFUNDED; the provider has no charge for the session. */
    RECORDED_NOT_CHARGED(false),
    /** The charged amount or currency differs from ours. */
    AMOUNT_MISMATCH(false),
    /** The provider refunded the charge; we don't show it refunded. Re-syncable. */
    REFUND_NOT_RECORDED(true),
    /** We show the payment REFUNDED; the provider never refunded the charge. Re-syncable. */
    REFUND_NOT_EXECUTED(true),
    /** The provider charged a session we have no payment for. */
    UNKNOWN_SESSION(false);

    private final boolean resyncable;

    Type(boolean resyncable) {
      this.resyncable = resyncable;
    }

    public boolean isResyncable() {
      return resyncable;
    }
  }

  public enum Status {
    OPEN,
    RESOLVED
  }

  public enum Resolution {
    /** An admin applied the provider's state. */
    RESYNCED,
    /** An admin closed it with a note. */
    NOTE,
    /** A later run found the records in agreement. */
    CLEARED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "run_id", nullable = false)
  private Long runId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Type type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status = Status.OPEN;

  @Enumerated(EnumType.STRING)
  private Resolution resolution;

  private String note;

  @Column(name = "resolved_by")
  private String resolvedBy;

  @Column(name = "payment_id")
  private Long paymentId;

  @Column(name = "order_id")
  private Long orderId;

  @Column(name = "checkout_session_id", nullable = false)
  private String checkoutSessionId;

  @Column(name = "our_status")
  private String ourStatus;

  @Column(name = "our_amount")
  private BigDecimal ourAmount;

  @Column(name = "our_currency")
  private String ourCurrency;

  @Column(name = "provider_status")
  private String providerStatus;

  @Column(name = "provider_amount")
  private BigDecimal providerAmount;

  @Column(name = "provider_currency")
  private String providerCurrency;

  @Column(name = "provider_charge_reference")
  private String providerChargeReference;

  @Column(name = "provider_refund_reference")
  private String providerRefundReference;

  private String detail;

  @Column(name = "first_seen_at", nullable = false)
  private Instant firstSeenAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  public void resolve(Resolution resolution, String by, String note, Instant at) {
    this.status = Status.RESOLVED;
    this.resolution = resolution;
    this.resolvedBy = by;
    this.note = note;
    this.resolvedAt = at;
  }
}
