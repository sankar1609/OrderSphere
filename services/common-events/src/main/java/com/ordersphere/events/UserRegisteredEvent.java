package com.ordersphere.events;

import lombok.Getter;

@Getter
public class UserRegisteredEvent extends BaseEvent {

  private final Long userId;
  private final String username;
  private final String role;

  public UserRegisteredEvent(Long userId, String username, String role) {
    super("UserRegisteredEvent");
    this.userId = userId;
    this.username = username;
    this.role = role;
  }
}
