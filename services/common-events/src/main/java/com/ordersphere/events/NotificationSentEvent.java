package com.ordersphere.events;

import lombok.Getter;

@Getter
public class NotificationSentEvent extends BaseEvent {

  private final Long notificationId;
  private final String recipientUsername;
  private final String channel;

  public NotificationSentEvent(Long notificationId, String recipientUsername, String channel) {
    super("NotificationSentEvent");
    this.notificationId = notificationId;
    this.recipientUsername = recipientUsername;
    this.channel = channel;
  }
}
