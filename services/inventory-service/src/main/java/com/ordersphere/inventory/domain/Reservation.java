package com.ordersphere.inventory.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false, unique = true)
  private Long orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ReservationStatus status;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ReservationItem> items = new ArrayList<>();

  public Reservation(Long orderId, Instant expiresAt) {
    this.orderId = orderId;
    this.status = ReservationStatus.ACTIVE;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  public void addItem(ReservationItem item) {
    item.setReservation(this);
    items.add(item);
  }
}
