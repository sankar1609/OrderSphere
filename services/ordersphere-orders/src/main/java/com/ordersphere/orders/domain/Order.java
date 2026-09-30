package com.ordersphere.orders.domain;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "customer_username", nullable = false)
  private String customerUsername;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "payment_id")
  private Long paymentId;

  /** Payment provider's hosted page where the customer pays; relevant while AWAITING_PAYMENT. */
  @Column(name = "checkout_url", length = 1024)
  private String checkoutUrl;

  @Column(name = "shipment_id")
  private Long shipmentId;

  /** Shipment creation attempts so far; the first is made when the order is confirmed. */
  @Column(name = "shipment_attempts", nullable = false)
  private int shipmentAttempts;

  /** When to retry creating the shipment; null once shipped or once retries are given up on. */
  @Column(name = "shipment_next_attempt_at")
  private Instant shipmentNextAttemptAt;

  @Column(name = "shipment_last_error", length = 1024)
  private String shipmentLastError;

  @Column(name = "shipping_destination")
  private String shippingDestination;

  @Column(name = "total_amount", precision = 12, scale = 2)
  private BigDecimal totalAmount;

  @Column(length = 3)
  private String currency;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderItem> items = new ArrayList<>();

  public Order(String customerUsername) {
    this.customerUsername = customerUsername;
    this.status = OrderStatus.PENDING;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void addItem(OrderItem item) {
    item.setOrder(this);
    items.add(item);
  }

  public void markStatus(OrderStatus newStatus) {
    this.status = newStatus;
    this.updatedAt = Instant.now();
  }
}
