package com.ordersphere.notification.dto;

import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.NotificationPreference;
import java.time.Instant;

public record NotificationPreferenceResponse(
    Long id, NotificationChannel channel, boolean enabled, Instant updatedAt) {

  public static NotificationPreferenceResponse from(NotificationPreference preference) {
    return new NotificationPreferenceResponse(
        preference.getId(),
        preference.getChannel(),
        preference.isEnabled(),
        preference.getUpdatedAt());
  }
}
