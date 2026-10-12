package com.ordersphere.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Where the chain currently ends. A single row (id 1), locked for the duration of every append. */
@Entity
@Table(name = "audit_chain_head")
public class ChainHead {

  public static final short ID = 1;

  /** What the first entry chains from. */
  public static final String GENESIS_HASH = "0".repeat(64);

  @Id private Short id;

  @Column(nullable = false)
  private long seq;

  @Column(nullable = false)
  private String hash;

  protected ChainHead() {}

  public long getSeq() {
    return seq;
  }

  public String getHash() {
    return hash;
  }

  public void advanceTo(long seq, String hash) {
    this.seq = seq;
    this.hash = hash;
  }
}
