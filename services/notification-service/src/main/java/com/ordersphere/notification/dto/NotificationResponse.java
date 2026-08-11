package com.ordersphere.notification.dto;

import com.ordersphere.notification.domain.Notification;
import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.NotificationStatus;
import com.ordersphere.notification.domain.TemplateKey;
import java.time.Instant;

public record NotificationResponse(
    Long id,
    String recipientUsername,
    NotificationChannel channel,
    TemplateKey templateKey,
    String message,
    NotificationStatus status,
    Instant createdAt,
    Instant sentAt) {

  public static NotificationResponse from(Notification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getRecipientUsername(),
        notification.getChannel(),
        notification.getTemplateKey(),
        notification.getMessage(),
        notification.getStatus(),
        notification.getCreatedAt(),
        notification.getSentAt());
  }
}
