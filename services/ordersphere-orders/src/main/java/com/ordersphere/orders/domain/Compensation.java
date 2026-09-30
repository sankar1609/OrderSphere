package com.ordersphere.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A refund or stock release the saga still owes another service (see V5 migration). */
@Entity
@Table(name = "order_compensations")
@Getter
@Setter
@NoArgsConstructor
public class Compensation {

  public enum Type {
    RELEASE_INVENTORY,
    REFUND_PAYMENT
  }

  public enum Status {
    PENDING,
    DONE,
    /** Rejected by the other service or out of retries - needs a person to look at it. */
    FAILED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Type type;

  @Column(name = "payment_id")
  private Long paymentId;

  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error")
  private String lastError;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Compensation(Type type, Long orderId, Long paymentId, String reason, Instant now) {
    this.type = type;
    this.orderId = orderId;
    this.paymentId = paymentId;
    this.reason = reason;
    this.status = Status.PENDING;
    this.nextAttemptAt = now;
    this.createdAt = now;
    this.updatedAt = now;
  }
}
