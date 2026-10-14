package com.ordersphere.notification.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ordersphere.security.testing.TestJwtIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "notification.alerts.webhook-token=test-alert-token",
      "notification.alerts.recipients=ops-admin, second-admin"
    })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class AlertWebhookIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;

  /** One group as Alertmanager posts it (version 4 of its webhook payload), trimmed. */
  private static String payload(String status, String alertname) {
    return """
        {
          "version": "4",
          "groupKey": "{}:{alertname=\\"%2$s\\"}",
          "status": "%1$s",
          "receiver": "ordersphere-notifications",
          "groupLabels": {"alertname": "%2$s"},
          "commonLabels": {"alertname": "%2$s"},
          "externalURL": "http://alertmanager:9093",
          "alerts": [
            {
              "status": "%1$s",
              "labels": {"alertname": "%2$s", "service": "shipping-service", "severity": "critical"},
              "annotations": {
                "summary": "shipping-service is not answering Prometheus",
                "description": "No successful scrape for 2 minutes."
              },
              "startsAt": "2026-10-14T10:00:00Z",
              "endsAt": "0001-01-01T00:00:00Z",
              "fingerprint": "abc123"
            }
          ]
        }
        """
        .formatted(status, alertname);
  }

  private MockHttpServletRequestBuilder webhook(String body) {
    return post("/notifications/alerts").contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private MockHttpServletRequestBuilder inboxOf(String username) {
    return get("/notifications")
        .header("Authorization", "Bearer " + TestJwtIssuer.token(username, "ADMIN"));
  }

  @Test
  void theWebhookAnswersOnlyTheWebhookToken() throws Exception {
    String body = payload("firing", "RejectedAlert");

    mockMvc.perform(webhook(body)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(webhook(body).header("Authorization", "Bearer wrong-token"))
        .andExpect(status().isUnauthorized());
    // An admin's JWT is the wrong key too: alerts come from Alertmanager, not from people.
    mockMvc
        .perform(
            webhook(body)
                .header("Authorization", "Bearer " + TestJwtIssuer.token("ops-admin", "ADMIN")))
        .andExpect(status().isUnauthorized());

    mockMvc
        .perform(inboxOf("ops-admin"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.message =~ /.*RejectedAlert.*/)]", hasSize(0)));
  }

  @Test
  void aFiringAlertBecomesAnInAppNotificationForEveryRecipient() throws Exception {
    mockMvc
        .perform(
            webhook(payload("firing", "ServiceDown"))
                .header("Authorization", "Bearer test-alert-token"))
        .andExpect(status().isNoContent());

    for (String recipient : new String[] {"ops-admin", "second-admin"}) {
      mockMvc
          .perform(inboxOf(recipient))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@.templateKey == 'SYSTEM_ALERT')]", hasSize(1)))
          .andExpect(jsonPath("$[?(@.templateKey == 'SYSTEM_ALERT')].channel").value("IN_APP"))
          .andExpect(
              jsonPath("$[?(@.templateKey == 'SYSTEM_ALERT')].message")
                  .value(
                      "[critical] ServiceDown: shipping-service is not answering Prometheus."
                          + " No successful scrape for 2 minutes."));
    }
  }

  @Test
  void aResolvedAlertTellsTheRecipientsItIsOver() throws Exception {
    mockMvc
        .perform(
            webhook(payload("resolved", "AuditChainBroken"))
                .header("Authorization", "Bearer test-alert-token"))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(inboxOf("ops-admin"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.templateKey == 'SYSTEM_ALERT_RESOLVED')].message")
                .value(
                    "Resolved: AuditChainBroken - shipping-service is not answering Prometheus"));
  }

  @Test
  void aPayloadWithoutAlertsIsAcceptedAndCreatesNothing() throws Exception {
    mockMvc
        .perform(
            webhook("{\"status\":\"firing\"}").header("Authorization", "Bearer test-alert-token"))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(inboxOf("nobody-else"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }
}
