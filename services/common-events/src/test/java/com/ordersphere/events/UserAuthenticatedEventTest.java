package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserAuthenticatedEventTest {

  @Test
  void carriesUserFieldsAndEventType() {
    UserAuthenticatedEvent event = new UserAuthenticatedEvent(1L, "alice");

    assertThat(event.getUserId()).isEqualTo(1L);
    assertThat(event.getUsername()).isEqualTo("alice");
    assertThat(event.getEventType()).isEqualTo("UserAuthenticatedEvent");
  }
}
