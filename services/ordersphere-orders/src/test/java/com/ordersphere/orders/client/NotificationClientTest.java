package com.ordersphere.orders.client;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NotificationClientTest {

  @Test
  void notifySendsExpectedRequest() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    NotificationClient client = new NotificationClient(builder);

    server
        .expect(requestTo("http://notification-service/notifications"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer token"))
        .andExpect(jsonPath("$.recipientUsername").value("alice"))
        .andExpect(jsonPath("$.channel").value("EMAIL"))
        .andExpect(jsonPath("$.templateKey").value("ORDER_CONFIRMED"))
        .andExpect(jsonPath("$.variables.orderId").value("1"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    client.notify(
        "alice",
        NotificationClient.TemplateKey.ORDER_CONFIRMED,
        Map.of("orderId", "1"),
        "Bearer token");

    server.verify();
  }

  @Test
  void notifySwallowsFailures() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    NotificationClient client = new NotificationClient(builder);

    server
        .expect(requestTo("http://notification-service/notifications"))
        .andRespond(withServerError());

    client.notify(
        "alice",
        NotificationClient.TemplateKey.ORDER_CANCELLED,
        Map.of("orderId", "1"),
        "Bearer token");

    server.verify();
  }
}
