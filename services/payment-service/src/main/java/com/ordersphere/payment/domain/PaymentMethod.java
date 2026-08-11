package com.ordersphere.payment.domain;

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

@Entity
@Table(name = "payment_methods")
@Getter
@Setter
@NoArgsConstructor
public class PaymentMethod {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "customer_username", nullable = false)
  private String customerUsername;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentMethodType type;

  @Column(nullable = false)
  private String token;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public PaymentMethod(String customerUsername, PaymentMethodType type, String token) {
    this.customerUsername = customerUsername;
    this.type = type;
    this.token = token;
    this.createdAt = Instant.now();
  }
}
