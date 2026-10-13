package com.ordersphere.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * A point at which the chain head was anchored outside this database (see AuditAnchor). Append-only
 * like the entries. Verification checks the chain still passes through every anchor.
 */
@Entity
@Immutable
@Table(name = "audit_anchors")
public class ChainAnchor {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private long seq;

  @Column(nullable = false, updatable = false)
  private String hash;

  @Column(nullable = false, updatable = false)
  private String target;

  @Column(name = "anchored_at", nullable = false, updatable = false)
  private Instant anchoredAt;

  protected ChainAnchor() {}

  public ChainAnchor(long seq, String hash, String target, Instant anchoredAt) {
    this.seq = seq;
    this.hash = hash;
    this.target = target;
    this.anchoredAt = anchoredAt;
  }

  public Long getId() {
    return id;
  }

  public long getSeq() {
    return seq;
  }

  public String getHash() {
    return hash;
  }

  public String getTarget() {
    return target;
  }

  public Instant getAnchoredAt() {
    return anchoredAt;
  }
}
