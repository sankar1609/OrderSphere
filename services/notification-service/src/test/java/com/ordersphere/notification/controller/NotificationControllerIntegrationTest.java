package com.ordersphere.notification.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.events.StockLowEvent;
import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.TemplateKey;
import com.ordersphere.notification.dto.CreateNotificationPreferenceRequest;
import com.ordersphere.notification.dto.CreateNotificationRequest;
import com.ordersphere.notification.service.NotificationDeliveryJob;
import com.ordersphere.security.testing.TestJwtIssuer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class NotificationControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private NotificationDeliveryJob notificationDeliveryJob;
  @Autowired private ApplicationEventPublisher eventPublisher;

  private String tokenFor(String username, String role) {
    return TestJwtIssuer.token(username, role);
  }

  @Test
  void aStockLowEventOnTheBrokerBecomesAnAlertForTheProductsVendor() throws Exception {
    // Published like inventory-service does: DomainEventRelay sends it to the exchange, and the
    // listener picks it up off notification-service.events.
    eventPublisher.publishEvent(new StockLowEvent("LOW-1", "Widget", 2, 5, "vendor-low"));

    String body = "[]";
    for (int i = 0; i < 50 && !body.contains("LOW-1"); i++) {
      Thread.sleep(200);
      body =
          mockMvc
              .perform(
                  get("/notifications")
                      .header("Authorization", "Bearer " + tokenFor("vendor-low", "VENDOR")))
              .andReturn()
              .getResponse()
              .getContentAsString();
    }

    mockMvc
        .perform(
            get("/notifications")
                .header("Authorization", "Bearer " + tokenFor("vendor-low", "VENDOR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].templateKey", is("STOCK_LOW")))
        .andExpect(jsonPath("$[0].channel", is("IN_APP")))
        .andExpect(
            jsonPath(
                "$[0].message",
                is("Low stock: LOW-1 (Widget) has 2 left - reorder threshold is 5.")));
  }

  @Test
  void notificationEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/notifications/1")).andExpect(status().isUnauthorized());
  }

  @Test
  void nonAdminCannotCreateNotification() throws Exception {
    mockMvc
        .perform(
            post("/notifications")
                .header("Authorization", "Bearer " + tokenFor("alice", "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateNotificationRequest(
                            "alice",
                            NotificationChannel.EMAIL,
                            TemplateKey.ORDER_CONFIRMED,
                            Map.of("orderId", "1")))))
        .andExpect(status().isForbidden());
  }

  @Test
  void serviceIdentityCanCreateNotification() throws Exception {
    mockMvc
        .perform(
            post("/notifications")
                .header("Authorization", "Bearer " + tokenFor("orders-service", "SERVICE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateNotificationRequest(
                            "alice",
                            NotificationChannel.EMAIL,
                            TemplateKey.ORDER_CONFIRMED,
                            Map.of("orderId", "1")))))
        .andExpect(status().isCreated());
  }

  @Test
  void createNotificationIsDeliveredBySweep() throws Exception {
    String createdBody =
        mockMvc
            .perform(
                post("/notifications")
                    .header("Authorization", "Bearer " + tokenFor("admin", "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateNotificationRequest(
                                "carol",
                                NotificationChannel.EMAIL,
                                TemplateKey.ORDER_CONFIRMED,
                                Map.of("orderId", "42")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("PENDING")))
            .andExpect(jsonPath("$.message", is("Your order #42 has been confirmed.")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long notificationId = objectMapper.readTree(createdBody).get("id").asLong();

    notificationDeliveryJob.deliverPendingNotifications();

    mockMvc
        .perform(
            get("/notifications/" + notificationId)
                .header("Authorization", "Bearer " + tokenFor("carol", "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("SENT")));
  }

  @Test
  void createNotificationIsSkippedWhenChannelDisabled() throws Exception {
    mockMvc
        .perform(
            post("/notification-preferences")
                .header("Authorization", "Bearer " + tokenFor("dave", "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateNotificationPreferenceRequest(NotificationChannel.SMS, false))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/notifications")
                .header("Authorization", "Bearer " + tokenFor("admin", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateNotificationRequest(
                            "dave",
                            NotificationChannel.SMS,
                            TemplateKey.DELIVERY_CONFIRMED,
                            Map.of("orderId", "7")))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status", is("SKIPPED")));
  }

  @Test
  void aUserCannotSeeAnotherUsersNotification() throws Exception {
    String createdBody =
        mockMvc
            .perform(
                post("/notifications")
                    .header("Authorization", "Bearer " + tokenFor("admin", "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateNotificationRequest(
                                "erin",
                                NotificationChannel.EMAIL,
                                TemplateKey.ORDER_CONFIRMED,
                                Map.of("orderId", "9")))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long notificationId = objectMapper.readTree(createdBody).get("id").asLong();

    mockMvc
        .perform(
            get("/notifications/" + notificationId)
                .header("Authorization", "Bearer " + tokenFor("frank", "CUSTOMER")))
        .andExpect(status().isNotFound());
  }
}
