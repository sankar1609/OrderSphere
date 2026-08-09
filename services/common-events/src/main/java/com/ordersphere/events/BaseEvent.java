package com.ordersphere.events;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
public abstract class BaseEvent {

  private final String eventId;
  private final String eventType;
  private final Instant timestamp;

  protected BaseEvent(String eventType) {
    this.eventId = UUID.randomUUID().toString();
    this.eventType = eventType;
    this.timestamp = Instant.now();
  }
}
