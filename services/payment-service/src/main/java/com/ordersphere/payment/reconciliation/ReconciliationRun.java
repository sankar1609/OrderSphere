package com.ordersphere.payment.reconciliation;

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

/** One comparison of our records with the provider's settlement report for a time window. */
@Entity
@Table(name = "reconciliation_runs")
@Getter
@Setter
@NoArgsConstructor
public class ReconciliationRun {

  public enum Status {
    RUNNING,
    SUCCEEDED,
    FAILED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "window_from", nullable = false)
  private Instant windowFrom;

  @Column(name = "window_to", nullable = false)
  private Instant windowTo;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(nullable = false)
  private int transactions;

  @Column(name = "payments_checked", nullable = false)
  private int paymentsChecked;

  @Column(name = "findings_opened", nullable = false)
  private int findingsOpened;

  @Column(name = "findings_cleared", nullable = false)
  private int findingsCleared;

  private String error;

  public ReconciliationRun(Instant windowFrom, Instant windowTo, Instant startedAt) {
    this.windowFrom = windowFrom;
    this.windowTo = windowTo;
    this.startedAt = startedAt;
    this.status = Status.RUNNING;
  }
}
