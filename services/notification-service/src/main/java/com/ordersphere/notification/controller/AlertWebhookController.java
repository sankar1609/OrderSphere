package com.ordersphere.notification.controller;

import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.TemplateKey;
import com.ordersphere.notification.dto.AlertmanagerWebhook;
import com.ordersphere.notification.dto.CreateNotificationRequest;
import com.ordersphere.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Alertmanager delivers the alerts Prometheus fires: each one becomes an in-app notification
 * for the configured recipients (the admins), once when it fires, again each time Alertmanager
 * repeats it, and once when it resolves.
 *
 * <p>Guarded by a shared token ({@code ALERT_WEBHOOK_TOKEN}), not a JWT: Alertmanager has no login,
 * and the API gateway routes to this service, so the endpoint can't be open. Without a configured
 * token it answers nobody. SecurityConfig permits the path; the check is here.
 */
@Tag(name = "Alerts", description = "Alertmanager's webhook")
@RestController
@RequestMapping("/notifications/alerts")
public class AlertWebhookController {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String UNKNOWN = "unknown";

  // The message column holds 1000 characters; these keep a rendered alert within it.
  private static final int MAX_NAME = 100;
  private static final int MAX_SEVERITY = 30;
  private static final int MAX_SUMMARY = 250;
  private static final int MAX_DESCRIPTION = 550;

  private final NotificationService notificationService;
  private final byte[] webhookToken;
  private final List<String> recipients;

  public AlertWebhookController(
      NotificationService notificationService,
      @Value("${notification.alerts.webhook-token:}") String webhookToken,
      @Value("${notification.alerts.recipients:admin}") String recipients) {
    this.notificationService = notificationService;
    this.webhookToken = webhookToken.trim().getBytes(StandardCharsets.UTF_8);
    this.recipients =
        Arrays.stream(recipients.split(",")).map(String::trim).filter(r -> !r.isEmpty()).toList();
  }

  @Operation(
      summary = "Receive alerts from Alertmanager",
      description = "Requires the alert webhook token as the Bearer credential, not a JWT.")
  @PostMapping
  public ResponseEntity<Void> receive(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestBody AlertmanagerWebhook webhook) {
    if (!allowed(authorization)) {
      return ResponseEntity.status(401).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
    }
    if (webhook.alerts() != null) {
      for (AlertmanagerWebhook.Alert alert : webhook.alerts()) {
        TemplateKey templateKey =
            alert.resolved() ? TemplateKey.SYSTEM_ALERT_RESOLVED : TemplateKey.SYSTEM_ALERT;
        Map<String, String> variables = variables(alert);
        for (String recipient : recipients) {
          notificationService.createNotification(
              new CreateNotificationRequest(
                  recipient, NotificationChannel.IN_APP, templateKey, variables));
        }
      }
    }
    return ResponseEntity.noContent().build();
  }

  private boolean allowed(String authorization) {
    if (webhookToken.length == 0
        || authorization == null
        || !authorization.startsWith(BEARER_PREFIX)) {
      return false;
    }
    byte[] presented =
        authorization.substring(BEARER_PREFIX.length()).getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(webhookToken, presented);
  }

  private static Map<String, String> variables(AlertmanagerWebhook.Alert alert) {
    Map<String, String> labels = alert.labels() == null ? Map.of() : alert.labels();
    Map<String, String> annotations = alert.annotations() == null ? Map.of() : alert.annotations();
    String alertname = labels.getOrDefault("alertname", UNKNOWN);
    return Map.of(
        "alertname", truncate(alertname, MAX_NAME),
        "severity", truncate(labels.getOrDefault("severity", UNKNOWN), MAX_SEVERITY),
        "summary", truncate(annotations.getOrDefault("summary", alertname), MAX_SUMMARY),
        "description", truncate(annotations.getOrDefault("description", ""), MAX_DESCRIPTION));
  }

  private static String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max - 3) + "...";
  }
}
