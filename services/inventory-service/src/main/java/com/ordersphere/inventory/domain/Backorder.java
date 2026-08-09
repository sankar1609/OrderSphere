package com.ordersphere.inventory.domain;

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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "backorders")
@Getter
@Setter
@NoArgsConstructor
public class Backorder {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Column(nullable = false)
  private int quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BackorderStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "fulfilled_at")
  private Instant fulfilledAt;

  public Backorder(Product product, Long orderId, int quantity) {
    this.product = product;
    this.orderId = orderId;
    this.quantity = quantity;
    this.status = BackorderStatus.OPEN;
    this.createdAt = Instant.now();
  }
}
