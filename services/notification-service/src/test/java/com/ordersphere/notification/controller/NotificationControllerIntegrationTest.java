package com.ordersphere.notification.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.TemplateKey;
import com.ordersphere.notification.dto.CreateNotificationPreferenceRequest;
import com.ordersphere.notification.dto.CreateNotificationRequest;
import com.ordersphere.notification.service.NotificationDeliveryJob;
import com.ordersphere.security.JwtTokenProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class NotificationControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private NotificationDeliveryJob notificationDeliveryJob;

  private String tokenFor(String username, String role) {
    return jwtTokenProvider.generateToken(username, Map.of("role", role));
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
