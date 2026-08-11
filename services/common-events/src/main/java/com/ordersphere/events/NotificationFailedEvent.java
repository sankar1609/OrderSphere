package com.ordersphere.events;

import lombok.Getter;

@Getter
public class NotificationFailedEvent extends BaseEvent {

  private final Long notificationId;
  private final String recipientUsername;
  private final String channel;
  private final String reason;

  public NotificationFailedEvent(
      Long notificationId, String recipientUsername, String channel, String reason) {
    super("NotificationFailedEvent");
    this.notificationId = notificationId;
    this.recipientUsername = recipientUsername;
    this.channel = channel;
    this.reason = reason;
  }
}
