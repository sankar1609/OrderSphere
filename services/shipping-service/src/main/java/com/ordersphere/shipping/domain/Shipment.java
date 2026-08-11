package com.ordersphere.shipping.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
public class Shipment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "order_id", nullable = false)
  private Long orderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ShipmentType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ShipmentStatus status;

  @Column(nullable = false)
  private String carrier;

  @Column(name = "tracking_number", nullable = false)
  private String trackingNumber;

  @Column(nullable = false)
  private String destination;

  @Column(name = "parent_shipment_id")
  private Long parentShipmentId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "delivered_at")
  private Instant deliveredAt;

  public Shipment(Long orderId, ShipmentType type, String destination, Long parentShipmentId) {
    this.orderId = orderId;
    this.type = type;
    this.destination = destination;
    this.parentShipmentId = parentShipmentId;
    this.status = ShipmentStatus.CREATED;
    this.carrier = "STUB-EXPRESS";
    this.trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void advanceTo(ShipmentStatus newStatus) {
    this.status = newStatus;
    this.updatedAt = Instant.now();
    if (newStatus == ShipmentStatus.DELIVERED) {
      this.deliveredAt = this.updatedAt;
    }
  }
}
