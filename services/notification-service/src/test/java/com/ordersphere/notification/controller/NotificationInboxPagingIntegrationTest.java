package com.ordersphere.notification.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.TemplateKey;
import com.ordersphere.notification.dto.CreateNotificationRequest;
import com.ordersphere.notification.service.NotificationService;
import com.ordersphere.security.testing.TestJwtIssuer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** GET /notifications: the caller's inbox a page at a time, newest first. */
@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class NotificationInboxPagingIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private NotificationService notificationService;

  /** Order confirmations #1..#count for the recipient, oldest first. */
  private void notificationsFor(String recipient, int count) {
    for (int i = 1; i <= count; i++) {
      notificationService.createNotification(
          new CreateNotificationRequest(
              recipient,
              NotificationChannel.EMAIL,
              TemplateKey.ORDER_CONFIRMED,
              Map.of("orderId", String.valueOf(i))));
    }
  }

  private MockHttpServletRequestBuilder inboxOf(String username) {
    return get("/notifications")
        .header("Authorization", TestJwtIssuer.bearer(username, "CUSTOMER"));
  }

  @Test
  void pagesAreNewestFirstAndCountTheWholeInbox() throws Exception {
    notificationsFor("reader", 5);

    mockMvc
        .perform(inboxOf("reader").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page", is(0)))
        .andExpect(jsonPath("$.size", is(2)))
        .andExpect(jsonPath("$.total", is(5)))
        .andExpect(
            jsonPath(
                "$.items[*].message",
                contains(
                    "Your order #5 has been confirmed.", "Your order #4 has been confirmed.")));
    mockMvc
        .perform(inboxOf("reader").param("size", "2").param("page", "2"))
        .andExpect(jsonPath("$.items[*].message", contains("Your order #1 has been confirmed.")));
  }

  @Test
  void withoutParametersTheNewestTwentyAreReturned() throws Exception {
    notificationsFor("busy", 23);

    mockMvc
        .perform(inboxOf("busy"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size", is(20)))
        .andExpect(jsonPath("$.items", hasSize(20)))
        .andExpect(jsonPath("$.total", is(23)))
        .andExpect(jsonPath("$.items[0].message", is("Your order #23 has been confirmed.")));
  }

  @Test
  void aUserOnlyPagesThroughTheirOwnNotifications() throws Exception {
    notificationsFor("mine", 2);
    notificationsFor("theirs", 3);

    mockMvc
        .perform(inboxOf("mine"))
        .andExpect(jsonPath("$.total", is(2)))
        .andExpect(jsonPath("$.items[*].recipientUsername", contains("mine", "mine")));
  }

  @Test
  void outOfRangeOrMalformedParametersAre400() throws Exception {
    for (String[] param :
        new String[][] {
          {"size", "0"}, {"size", "201"}, {"page", "-1"}, {"page", "2147483647"}, {"page", "next"}
        }) {
      mockMvc
          .perform(inboxOf("anyone").param(param[0], param[1]))
          .andExpect(status().isBadRequest());
    }
  }
}
