package com.ordersphere.notification.channel;

import com.ordersphere.notification.domain.NotificationChannel;

public interface NotificationChannelClient {

  DeliveryResult deliver(NotificationChannel channel, String recipientUsername, String message);
}
