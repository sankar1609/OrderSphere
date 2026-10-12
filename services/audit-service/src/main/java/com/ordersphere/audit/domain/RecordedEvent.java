package com.ordersphere.audit.domain;

import java.time.Instant;

/**
 * A domain event as it arrived from the broker, ready to be appended to the chain. {@code payload}
 * is the message body exactly as received. {@code occurredAt} is truncated to milliseconds: the
 * hash covers it, and the database wouldn't give back anything finer.
 */
public record RecordedEvent(
    String eventId,
    String eventType,
    String routingKey,
    String sourceService,
    String actor,
    Long orderId,
    String subjectUsername,
    String traceId,
    Instant occurredAt,
    String payload) {}
