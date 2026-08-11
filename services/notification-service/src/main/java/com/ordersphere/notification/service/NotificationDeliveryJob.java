package com.ordersphere.notification.service;

import com.ordersphere.notification.domain.NotificationStatus;
import com.ordersphere.notification.repository.NotificationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationDeliveryJob {

  private final NotificationRepository notificationRepository;
  private final NotificationService notificationService;

  public NotificationDeliveryJob(
      NotificationRepository notificationRepository, NotificationService notificationService) {
    this.notificationRepository = notificationRepository;
    this.notificationService = notificationService;
  }

  @Scheduled(fixedDelayString = "${notification.delivery.sweep-interval-ms}")
  public void sweep() {
    deliverPendingNotifications();
  }

  public void deliverPendingNotifications() {
    notificationRepository
        .findByStatus(NotificationStatus.PENDING)
        .forEach(notificationService::deliver);
  }
}
