package com.ordersphere.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refunds")
@Getter
@Setter
@NoArgsConstructor
public class Refund {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "payment_id", nullable = false)
  private Payment payment;

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RefundStatus status;

  @Column(name = "gateway_reference")
  private String gatewayReference;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Refund(Payment payment, BigDecimal amount, String reason) {
    this.payment = payment;
    this.amount = amount;
    this.reason = reason;
    this.status = RefundStatus.PENDING;
    this.createdAt = Instant.now();
  }
}
