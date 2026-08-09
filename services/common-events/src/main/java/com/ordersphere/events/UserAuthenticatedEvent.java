package com.ordersphere.events;

import lombok.Getter;

@Getter
public class UserAuthenticatedEvent extends BaseEvent {

  private final Long userId;
  private final String username;

  public UserAuthenticatedEvent(Long userId, String username) {
    super("UserAuthenticatedEvent");
    this.userId = userId;
    this.username = username;
  }
}
