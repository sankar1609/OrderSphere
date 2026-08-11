package com.ordersphere.shipping.domain;

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
@Table(name = "shipment_tracking_events")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentTrackingEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "shipment_id", nullable = false)
  private Shipment shipment;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ShipmentStatus status;

  @Column(nullable = false)
  private String location;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  public ShipmentTrackingEvent(Shipment shipment, ShipmentStatus status, String location) {
    this.shipment = shipment;
    this.status = status;
    this.location = location;
    this.occurredAt = Instant.now();
  }
}
