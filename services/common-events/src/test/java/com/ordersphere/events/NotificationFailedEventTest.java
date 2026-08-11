package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationFailedEventTest {

  @Test
  void carriesNotificationFieldsAndEventType() {
    NotificationFailedEvent event = new NotificationFailedEvent(1L, "alice", "EMAIL", "bounced");

    assertThat(event.getNotificationId()).isEqualTo(1L);
    assertThat(event.getRecipientUsername()).isEqualTo("alice");
    assertThat(event.getChannel()).isEqualTo("EMAIL");
    assertThat(event.getReason()).isEqualTo("bounced");
    assertThat(event.getEventType()).isEqualTo("NotificationFailedEvent");
  }
}
