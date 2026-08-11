package com.ordersphere.notification.service;

import com.ordersphere.events.NotificationFailedEvent;
import com.ordersphere.events.NotificationSentEvent;
import com.ordersphere.notification.channel.DeliveryResult;
import com.ordersphere.notification.channel.NotificationChannelClient;
import com.ordersphere.notification.domain.Notification;
import com.ordersphere.notification.domain.NotificationPreference;
import com.ordersphere.notification.domain.NotificationStatus;
import com.ordersphere.notification.dto.CreateNotificationRequest;
import com.ordersphere.notification.dto.NotificationResponse;
import com.ordersphere.notification.exception.NotificationNotFoundException;
import com.ordersphere.notification.repository.NotificationPreferenceRepository;
import com.ordersphere.notification.repository.NotificationRepository;
import com.ordersphere.notification.template.NotificationTemplateRenderer;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final NotificationPreferenceRepository preferenceRepository;
  private final NotificationTemplateRenderer templateRenderer;
  private final NotificationChannelClient channelClient;
  private final ApplicationEventPublisher eventPublisher;
  private final int maxRetries;

  public NotificationService(
      NotificationRepository notificationRepository,
      NotificationPreferenceRepository preferenceRepository,
      NotificationTemplateRenderer templateRenderer,
      NotificationChannelClient channelClient,
      ApplicationEventPublisher eventPublisher,
      @Value("${notification.delivery.max-retries}") int maxRetries) {
    this.notificationRepository = notificationRepository;
    this.preferenceRepository = preferenceRepository;
    this.templateRenderer = templateRenderer;
    this.channelClient = channelClient;
    this.eventPublisher = eventPublisher;
    this.maxRetries = maxRetries;
  }

  @Transactional
  public NotificationResponse createNotification(CreateNotificationRequest request) {
    Map<String, String> variables = request.variables() == null ? Map.of() : request.variables();
    String message = templateRenderer.render(request.templateKey(), variables);

    boolean enabled =
        preferenceRepository
            .findByUsernameAndChannel(request.recipientUsername(), request.channel())
            .map(NotificationPreference::isEnabled)
            .orElse(true);

    Notification notification =
        new Notification(
            request.recipientUsername(), request.channel(), request.templateKey(), message);
    if (!enabled) {
      notification.markStatus(NotificationStatus.SKIPPED);
    }
    notificationRepository.save(notification);

    return NotificationResponse.from(notification);
  }

  @Transactional(readOnly = true)
  public NotificationResponse getNotification(String username, boolean isAdmin, Long id) {
    return NotificationResponse.from(findNotificationOrThrow(username, isAdmin, id));
  }

  @Transactional(readOnly = true)
  public List<NotificationResponse> listOwn(String username) {
    return notificationRepository.findByRecipientUsername(username).stream()
        .map(NotificationResponse::from)
        .toList();
  }

  @Transactional
  public void deliver(Notification notification) {
    if (notification.getStatus() != NotificationStatus.PENDING) {
      return;
    }

    DeliveryResult result =
        channelClient.deliver(
            notification.getChannel(),
            notification.getRecipientUsername(),
            notification.getMessage());

    switch (result.outcome()) {
      case SUCCESS -> {
        notification.markStatus(NotificationStatus.SENT);
        notificationRepository.save(notification);
        eventPublisher.publishEvent(
            new NotificationSentEvent(
                notification.getId(),
                notification.getRecipientUsername(),
                notification.getChannel().name()));
      }
      case PERMANENT_FAILURE -> failNotification(notification, "Delivery permanently failed");
      case TRANSIENT_FAILURE -> {
        notification.setRetryCount(notification.getRetryCount() + 1);
        if (notification.getRetryCount() >= maxRetries) {
          failNotification(notification, "Delivery failed after " + maxRetries + " retries");
        } else {
          notificationRepository.save(notification);
        }
      }
    }
  }

  private void failNotification(Notification notification, String reason) {
    notification.markStatus(NotificationStatus.FAILED);
    notificationRepository.save(notification);
    eventPublisher.publishEvent(
        new NotificationFailedEvent(
            notification.getId(),
            notification.getRecipientUsername(),
            notification.getChannel().name(),
            reason));
  }

  private Notification findNotificationOrThrow(String username, boolean isAdmin, Long id) {
    if (isAdmin) {
      return notificationRepository
          .findById(id)
          .orElseThrow(() -> new NotificationNotFoundException(id));
    }
    return notificationRepository
        .findByIdAndRecipientUsername(id, username)
        .orElseThrow(() -> new NotificationNotFoundException(id));
  }
}
