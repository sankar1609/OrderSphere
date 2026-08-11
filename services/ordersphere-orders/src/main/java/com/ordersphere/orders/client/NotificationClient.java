package com.ordersphere.orders.client;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class NotificationClient {

  private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

  private final RestClient restClient;

  public NotificationClient(RestClient.Builder loadBalancedRestClientBuilder) {
    this.restClient = loadBalancedRestClientBuilder.baseUrl("http://notification-service").build();
  }

  public void notify(
      String recipientUsername,
      TemplateKey templateKey,
      Map<String, String> variables,
      String bearerToken) {
    try {
      restClient
          .post()
          .uri("/notifications")
          .header(HttpHeaders.AUTHORIZATION, bearerToken)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              new CreateNotificationRequest(
                  recipientUsername, NotificationChannel.EMAIL, templateKey, variables))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException ex) {
      log.warn(
          "Failed to send {} notification to {}: {}",
          templateKey,
          recipientUsername,
          ex.getMessage());
    }
  }

  public enum TemplateKey {
    ORDER_CONFIRMED,
    ORDER_CANCELLED
  }

  public enum NotificationChannel {
    EMAIL
  }

  public record CreateNotificationRequest(
      String recipientUsername,
      NotificationChannel channel,
      TemplateKey templateKey,
      Map<String, String> variables) {}
}
