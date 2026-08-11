package com.ordersphere.notification.dto;

import com.ordersphere.notification.domain.NotificationChannel;
import jakarta.validation.constraints.NotNull;

public record CreateNotificationPreferenceRequest(
    @NotNull NotificationChannel channel, @NotNull Boolean enabled) {}
