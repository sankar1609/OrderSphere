package com.ordersphere.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "recipient_username", nullable = false)
  private String recipientUsername;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(name = "template_key", nullable = false)
  private TemplateKey templateKey;

  @Column(nullable = false)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationStatus status;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  public Notification(
      String recipientUsername,
      NotificationChannel channel,
      TemplateKey templateKey,
      String message) {
    this.recipientUsername = recipientUsername;
    this.channel = channel;
    this.templateKey = templateKey;
    this.message = message;
    this.status = NotificationStatus.PENDING;
    this.retryCount = 0;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void markStatus(NotificationStatus newStatus) {
    this.status = newStatus;
    this.updatedAt = Instant.now();
    if (newStatus == NotificationStatus.SENT) {
      this.sentAt = this.updatedAt;
    }
  }
}
