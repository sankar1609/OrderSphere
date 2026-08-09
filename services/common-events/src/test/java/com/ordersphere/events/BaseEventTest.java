package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BaseEventTest {

  private static class TestEvent extends BaseEvent {
    TestEvent() {
      super("TestEvent");
    }
  }

  @Test
  void populatesEventIdTypeAndTimestampOnConstruction() {
    Instant before = Instant.now();

    TestEvent event = new TestEvent();

    assertThat(event.getEventId()).isNotBlank();
    assertThat(event.getEventType()).isEqualTo("TestEvent");
    assertThat(event.getTimestamp()).isNotNull().isAfterOrEqualTo(before);
  }

  @Test
  void generatesUniqueEventIdsPerInstance() {
    TestEvent first = new TestEvent();
    TestEvent second = new TestEvent();

    assertThat(first.getEventId()).isNotEqualTo(second.getEventId());
  }
}
