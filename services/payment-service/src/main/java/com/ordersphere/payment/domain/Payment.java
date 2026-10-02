package com.ordersphere.payment.domain;

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

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false, unique = true)
  private Long orderId;

  @Column(name = "customer_username", nullable = false)
  private String customerUsername;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentStatus status;

  @Column(name = "gateway_reference")
  private String gatewayReference;

  /** The provider's hosted checkout session the customer pays on. */
  @Column(name = "checkout_session_id", unique = true)
  private String checkoutSessionId;

  @Column(name = "checkout_url")
  private String checkoutUrl;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Payment(Long orderId, String customerUsername, BigDecimal amount, String currency) {
    this.orderId = orderId;
    this.customerUsername = customerUsername;
    this.amount = amount;
    this.currency = currency;
    this.status = PaymentStatus.PENDING;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void markStatus(PaymentStatus newStatus) {
    this.status = newStatus;
    this.updatedAt = Instant.now();
  }
}
