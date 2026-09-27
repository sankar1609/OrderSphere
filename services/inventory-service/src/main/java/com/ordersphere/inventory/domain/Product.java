package com.ordersphere.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String sku;

  @Column(nullable = false)
  private String name;

  @Column(name = "quantity_on_hand", nullable = false)
  private int quantityOnHand;

  @Column(name = "quantity_reserved", nullable = false)
  private int quantityReserved;

  @Column(name = "reorder_threshold", nullable = false)
  private int reorderThreshold;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Product(
      String sku, String name, int quantityOnHand, int reorderThreshold, BigDecimal unitPrice) {
    this.sku = sku;
    this.name = name;
    this.quantityOnHand = quantityOnHand;
    this.quantityReserved = 0;
    this.reorderThreshold = reorderThreshold;
    this.unitPrice = unitPrice;
    this.createdAt = Instant.now();
  }

  public int getAvailableQuantity() {
    return quantityOnHand - quantityReserved;
  }
}
