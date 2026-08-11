package com.ordersphere.notification.channel;

import com.ordersphere.notification.domain.NotificationChannel;
import org.springframework.stereotype.Component;

@Component
public class StubNotificationChannelClient implements NotificationChannelClient {

  public static final String PERMANENT_FAILURE_RECIPIENT = "fail-user";
  public static final String TRANSIENT_FAILURE_RECIPIENT = "retry-user";

  @Override
  public DeliveryResult deliver(
      NotificationChannel channel, String recipientUsername, String message) {
    if (PERMANENT_FAILURE_RECIPIENT.equals(recipientUsername)) {
      return new DeliveryResult(DeliveryResult.Outcome.PERMANENT_FAILURE);
    }
    if (TRANSIENT_FAILURE_RECIPIENT.equals(recipientUsername)) {
      return new DeliveryResult(DeliveryResult.Outcome.TRANSIENT_FAILURE);
    }
    return new DeliveryResult(DeliveryResult.Outcome.SUCCESS);
  }
}
