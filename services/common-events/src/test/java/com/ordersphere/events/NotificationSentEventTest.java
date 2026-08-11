package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationSentEventTest {

  @Test
  void carriesNotificationFieldsAndEventType() {
    NotificationSentEvent event = new NotificationSentEvent(1L, "alice", "EMAIL");

    assertThat(event.getNotificationId()).isEqualTo(1L);
    assertThat(event.getRecipientUsername()).isEqualTo("alice");
    assertThat(event.getChannel()).isEqualTo("EMAIL");
    assertThat(event.getEventType()).isEqualTo("NotificationSentEvent");
  }
}
