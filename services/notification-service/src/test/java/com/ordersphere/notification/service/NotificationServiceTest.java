package com.ordersphere.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.events.NotificationFailedEvent;
import com.ordersphere.events.NotificationSentEvent;
import com.ordersphere.notification.channel.DeliveryResult;
import com.ordersphere.notification.channel.NotificationChannelClient;
import com.ordersphere.notification.channel.StubNotificationChannelClient;
import com.ordersphere.notification.domain.Notification;
import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.NotificationPreference;
import com.ordersphere.notification.domain.NotificationStatus;
import com.ordersphere.notification.domain.TemplateKey;
import com.ordersphere.notification.dto.CreateNotificationRequest;
import com.ordersphere.notification.dto.NotificationResponse;
import com.ordersphere.notification.exception.NotificationNotFoundException;
import com.ordersphere.notification.repository.NotificationPreferenceRepository;
import com.ordersphere.notification.repository.NotificationRepository;
import com.ordersphere.notification.template.NotificationTemplateRenderer;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private NotificationRepository notificationRepository;
  @Mock private NotificationPreferenceRepository preferenceRepository;
  @Mock private NotificationChannelClient channelClient;
  @Mock private ApplicationEventPublisher eventPublisher;

  private final NotificationTemplateRenderer templateRenderer = new NotificationTemplateRenderer();

  private NotificationService notificationService;

  @BeforeEach
  void setUp() {
    notificationService =
        new NotificationService(
            notificationRepository,
            preferenceRepository,
            templateRenderer,
            channelClient,
            eventPublisher,
            3);
  }

  @Test
  void createNotificationSkipsWhenChannelDisabled() {
    NotificationPreference disabled =
        new NotificationPreference("alice", NotificationChannel.SMS, false);
    when(preferenceRepository.findByUsernameAndChannel("alice", NotificationChannel.SMS))
        .thenReturn(Optional.of(disabled));

    NotificationResponse response =
        notificationService.createNotification(
            new CreateNotificationRequest(
                "alice",
                NotificationChannel.SMS,
                TemplateKey.ORDER_CONFIRMED,
                Map.of("orderId", "1")));

    assertThat(response.status()).isEqualTo(NotificationStatus.SKIPPED);
  }

  @Test
  void createNotificationQueuesPendingWhenNoPreferenceExists() {
    when(preferenceRepository.findByUsernameAndChannel("alice", NotificationChannel.EMAIL))
        .thenReturn(Optional.empty());

    NotificationResponse response =
        notificationService.createNotification(
            new CreateNotificationRequest(
                "alice",
                NotificationChannel.EMAIL,
                TemplateKey.ORDER_CONFIRMED,
                Map.of("orderId", "1")));

    assertThat(response.status()).isEqualTo(NotificationStatus.PENDING);
    assertThat(response.message()).isEqualTo("Your order #1 has been confirmed.");
  }

  @Test
  void getNotificationRejectsNonOwner() {
    when(notificationRepository.findByIdAndRecipientUsername(5L, "bob"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> notificationService.getNotification("bob", false, 5L))
        .isInstanceOf(NotificationNotFoundException.class);
  }

  @Test
  void deliverMarksSentOnSuccessAndPublishesEvent() {
    Notification notification =
        new Notification("alice", NotificationChannel.EMAIL, TemplateKey.ORDER_CONFIRMED, "hi");
    when(channelClient.deliver(NotificationChannel.EMAIL, "alice", "hi"))
        .thenReturn(new DeliveryResult(DeliveryResult.Outcome.SUCCESS));

    notificationService.deliver(notification);

    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
    verify(eventPublisher).publishEvent(any(NotificationSentEvent.class));
  }

  @Test
  void deliverFailsImmediatelyOnPermanentFailure() {
    Notification notification =
        new Notification(
            StubNotificationChannelClient.PERMANENT_FAILURE_RECIPIENT,
            NotificationChannel.EMAIL,
            TemplateKey.ORDER_CONFIRMED,
            "hi");
    when(channelClient.deliver(
            NotificationChannel.EMAIL,
            StubNotificationChannelClient.PERMANENT_FAILURE_RECIPIENT,
            "hi"))
        .thenReturn(new DeliveryResult(DeliveryResult.Outcome.PERMANENT_FAILURE));

    notificationService.deliver(notification);

    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
    assertThat(notification.getRetryCount()).isZero();
    verify(eventPublisher).publishEvent(any(NotificationFailedEvent.class));
  }

  @Test
  void deliverRetriesTransientFailureThenFailsAtMax() {
    Notification notification =
        new Notification(
            StubNotificationChannelClient.TRANSIENT_FAILURE_RECIPIENT,
            NotificationChannel.EMAIL,
            TemplateKey.ORDER_CONFIRMED,
            "hi");
    when(channelClient.deliver(
            NotificationChannel.EMAIL,
            StubNotificationChannelClient.TRANSIENT_FAILURE_RECIPIENT,
            "hi"))
        .thenReturn(new DeliveryResult(DeliveryResult.Outcome.TRANSIENT_FAILURE));

    notificationService.deliver(notification);
    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
    assertThat(notification.getRetryCount()).isEqualTo(1);

    notificationService.deliver(notification);
    assertThat(notification.getRetryCount()).isEqualTo(2);

    notificationService.deliver(notification);
    assertThat(notification.getRetryCount()).isEqualTo(3);
    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
    verify(eventPublisher).publishEvent(any(NotificationFailedEvent.class));
  }
}
