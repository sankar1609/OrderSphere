package com.ordersphere.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reservation_items")
@Getter
@Setter
@NoArgsConstructor
public class ReservationItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "reservation_id", nullable = false)
  private Reservation reservation;

  @ManyToOne
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(name = "quantity_reserved", nullable = false)
  private int quantityReserved;

  public ReservationItem(Product product, int quantityReserved) {
    this.product = product;
    this.quantityReserved = quantityReserved;
  }
}
