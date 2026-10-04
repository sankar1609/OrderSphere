package com.ordersphere.dummygateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    properties = {
      "gateway.public-url=http://gw.test",
      "gateway.api-key=test-key",
      "gateway.webhook-secret=test-secret"
    })
@AutoConfigureMockMvc
@AutoConfigureMockRestServiceServer
@Testcontainers
class DummyPaymentGatewayIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  private static final String AUTH = "Bearer test-key";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private MockRestServiceServer webhookServer;

  @BeforeEach
  void resetWebhooks() {
    webhookServer.reset();
  }

  private JsonNode createSession() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/checkout-sessions")
                    .header("Authorization", AUTH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"merchantReference": "42", "amount": 6.00, "currency": "USD",
                         "description": "Order #7",
                         "successUrl": "http://shop/?payment=success",
                         "cancelUrl": "http://shop/?payment=cancelled",
                         "webhookUrl": "http://merchant/webhook"}
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.url", startsWith("http://gw.test/checkout/cs_")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body);
  }

  @Test
  void merchantApiRequiresTheSecretKey() throws Exception {
    mockMvc
        .perform(
            post("/api/checkout-sessions")
                .header("Authorization", "Bearer wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/checkout-sessions/cs_x")).andExpect(status().isUnauthorized());
  }

  @Test
  void payingSendsSignedWebhookAndRedirectsToSuccessUrl() throws Exception {
    String sessionId = createSession().get("id").asText();
    webhookServer
        .expect(requestTo("http://merchant/webhook"))
        .andExpect(
            MockRestRequestMatchers.header(WebhookSender.SIGNATURE_HEADER, startsWith("sha256=")))
        .andExpect(MockRestRequestMatchers.jsonPath("$.type").value("checkout.session.completed"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.sessionId").value(sessionId))
        .andExpect(MockRestRequestMatchers.jsonPath("$.merchantReference").value("42"))
        .andRespond(withSuccess());

    mockMvc
        .perform(get("/checkout/" + sessionId))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("6.00 USD")));

    mockMvc
        .perform(
            post("/checkout/" + sessionId + "/pay")
                .param("name", "Alice")
                .param("cardNumber", "4000 0000 0000 0002")
                .param("expiry", "12/30")
                .param("cvc", "123"))
        .andExpect(status().isPaymentRequired())
        .andExpect(content().string(containsString("declined")));

    mockMvc
        .perform(
            post("/checkout/" + sessionId + "/pay")
                .param("name", "Alice")
                .param("cardNumber", "4242 4242 4242 4242")
                .param("expiry", "12/30")
                .param("cvc", "123"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "http://shop/?payment=success"));

    webhookServer.verify(java.time.Duration.ofSeconds(5)); // delivered in the background
    mockMvc
        .perform(get("/api/checkout-sessions/" + sessionId).header("Authorization", AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
        .andExpect(jsonPath("$.chargeReference", startsWith("ch_")));

    // The settlement report lists the charge (and needs the merchant key like the rest of /api).
    String window =
        "?from="
            + java.time.Instant.now().minusSeconds(3600)
            + "&to="
            + java.time.Instant.now().plusSeconds(60);
    mockMvc.perform(get("/api/reports/transactions" + window)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/reports/transactions" + window).header("Authorization", AUTH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.sessionId == '" + sessionId + "')].type").value("CHARGE"))
        .andExpect(jsonPath("$[?(@.sessionId == '" + sessionId + "')].amount").value(6.0));
  }

  @Test
  void cancellingRedirectsToCancelUrlAndRefundsNeedAKnownCharge() throws Exception {
    String sessionId = createSession().get("id").asText();
    webhookServer
        .expect(requestTo("http://merchant/webhook"))
        .andExpect(MockRestRequestMatchers.jsonPath("$.type").value("checkout.session.cancelled"))
        .andRespond(withSuccess());

    mockMvc
        .perform(post("/checkout/" + sessionId + "/cancel"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", "http://shop/?payment=cancelled"));
    mockMvc.perform(post("/checkout/" + sessionId + "/cancel")).andExpect(status().isConflict());
    webhookServer.verify(java.time.Duration.ofSeconds(5)); // delivered in the background

    mockMvc
        .perform(
            post("/api/refunds")
                .header("Authorization", AUTH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chargeReference\": \"ch_unknown\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void signatureIsHmacSha256OfBody() {
    // Reference value: printf '%s' '{"a":1}' | openssl dgst -sha256 -hmac secret
    assertThat(WebhookSender.sign("{\"a\":1}", "secret"))
        .isEqualTo("sha256=aa9e2e3575f5d7098b6caccd790888c36d5fdb63342a73bada2d6a51747a8494");
  }
}
