package com.ordersphere.notification.dto;

import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.TemplateKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CreateNotificationRequest(
    @NotBlank String recipientUsername,
    @NotNull NotificationChannel channel,
    @NotNull TemplateKey templateKey,
    Map<String, String> variables) {}
