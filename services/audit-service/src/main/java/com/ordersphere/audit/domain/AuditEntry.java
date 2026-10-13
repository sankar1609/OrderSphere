package com.ordersphere.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * One recorded domain event. Rows are only ever inserted: the entity is immutable to Hibernate and
 * the table rejects UPDATE/DELETE (see V1). {@code hash} covers {@code prevHash} and the content
 * (see ChainHasher), which is what makes the log tamper-evident.
 */
@Entity
@Immutable
@Table(name = "audit_entries")
public class AuditEntry {

  @Id private Long seq;

  @Column(name = "event_id", nullable = false, updatable = false)
  private String eventId;

  @Column(name = "event_type", nullable = false, updatable = false)
  private String eventType;

  @Column(name = "routing_key", updatable = false)
  private String routingKey;

  @Column(name = "source_service", updatable = false)
  private String sourceService;

  @Column(updatable = false)
  private String actor;

  @Column(name = "order_id", updatable = false)
  private Long orderId;

  @Column(name = "subject_username", updatable = false)
  private String subjectUsername;

  @Column(name = "trace_id", updatable = false)
  private String traceId;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "prev_hash", nullable = false, updatable = false)
  private String prevHash;

  @Column(nullable = false, updatable = false)
  private String hash;

  protected AuditEntry() {}

  public AuditEntry(
      long seq, RecordedEvent event, Instant recordedAt, String prevHash, String hash) {
    this.seq = seq;
    this.eventId = event.eventId();
    this.eventType = event.eventType();
    this.routingKey = event.routingKey();
    this.sourceService = event.sourceService();
    this.actor = event.actor();
    this.orderId = event.orderId();
    this.subjectUsername = event.subjectUsername();
    this.traceId = event.traceId();
    this.occurredAt = event.occurredAt();
    this.recordedAt = recordedAt;
    this.payload = event.payload();
    this.prevHash = prevHash;
    this.hash = hash;
  }

  public Long getSeq() {
    return seq;
  }

  public String getEventId() {
    return eventId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getRoutingKey() {
    return routingKey;
  }

  public String getSourceService() {
    return sourceService;
  }

  public String getActor() {
    return actor;
  }

  public Long getOrderId() {
    return orderId;
  }

  public String getSubjectUsername() {
    return subjectUsername;
  }

  public String getTraceId() {
    return traceId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public String getPayload() {
    return payload;
  }

  public String getPrevHash() {
    return prevHash;
  }

  public String getHash() {
    return hash;
  }
}
