package com.ordersphere.audit.web;

import com.ordersphere.audit.domain.AuditEntry;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** An audit entry. {@code payload}, {@code prevHash} and {@code hash} are left out of lists. */
public record AuditEntryResponse(
    long seq,
    String eventId,
    String eventType,
    String sourceService,
    @Schema(description = "Who the publishing service was acting for; absent for scheduled jobs")
        String actor,
    Long orderId,
    @Schema(description = "The user the event is about, when the event names one")
        String subjectUsername,
    String traceId,
    Instant occurredAt,
    Instant recordedAt,
    @Schema(description = "The event exactly as it was published (JSON). Detail view only")
        String payload,
    String prevHash,
    String hash) {

  public static AuditEntryResponse summary(AuditEntry entry) {
    return from(entry, false);
  }

  public static AuditEntryResponse detail(AuditEntry entry) {
    return from(entry, true);
  }

  private static AuditEntryResponse from(AuditEntry entry, boolean detail) {
    return new AuditEntryResponse(
        entry.getSeq(),
        entry.getEventId(),
        entry.getEventType(),
        entry.getSourceService(),
        entry.getActor(),
        entry.getOrderId(),
        entry.getSubjectUsername(),
        entry.getTraceId(),
        entry.getOccurredAt(),
        entry.getRecordedAt(),
        detail ? entry.getPayload() : null,
        detail ? entry.getPrevHash() : null,
        detail ? entry.getHash() : null);
  }
}
