package com.ordersphere.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserRegisteredEventTest {

  @Test
  void carriesUserFieldsAndEventType() {
    UserRegisteredEvent event = new UserRegisteredEvent(1L, "alice", "CUSTOMER");

    assertThat(event.getUserId()).isEqualTo(1L);
    assertThat(event.getUsername()).isEqualTo("alice");
    assertThat(event.getRole()).isEqualTo("CUSTOMER");
    assertThat(event.getEventType()).isEqualTo("UserRegisteredEvent");
  }
}
