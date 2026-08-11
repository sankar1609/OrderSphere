package com.ordersphere.notification.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ordersphere.notification.domain.Notification;
import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.NotificationStatus;
import com.ordersphere.notification.domain.TemplateKey;
import com.ordersphere.notification.repository.NotificationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryJobTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private NotificationService notificationService;

  @Test
  void deliverPendingNotificationsDeliversEachPendingNotification() {
    Notification pending =
        new Notification("alice", NotificationChannel.EMAIL, TemplateKey.ORDER_CONFIRMED, "hi");
    when(notificationRepository.findByStatus(NotificationStatus.PENDING))
        .thenReturn(List.of(pending));

    NotificationDeliveryJob job =
        new NotificationDeliveryJob(notificationRepository, notificationService);
    job.deliverPendingNotifications();

    verify(notificationService).deliver(pending);
  }

  @Test
  void deliverPendingNotificationsDoesNothingWhenNonePending() {
    when(notificationRepository.findByStatus(NotificationStatus.PENDING)).thenReturn(List.of());

    NotificationDeliveryJob job =
        new NotificationDeliveryJob(notificationRepository, notificationService);
    job.deliverPendingNotifications();

    verifyNoInteractions(notificationService);
  }
}
