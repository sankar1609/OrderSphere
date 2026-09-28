package com.ordersphere.payment.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.payment.dto.CreatePaymentRequest;
import com.ordersphere.payment.dto.RefundRequest;
import com.ordersphere.payment.gateway.PaymentGatewayClient;
import com.ordersphere.payment.gateway.PaymentGatewayClient.CheckoutSession;
import com.ordersphere.payment.gateway.PaymentGatewayClient.SessionStatus;
import com.ordersphere.payment.gateway.PaymentGatewayException;
import com.ordersphere.payment.service.PaymentProcessingJob;
import com.ordersphere.security.JwtTokenProvider;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    properties = {"eureka.client.enabled=false", "payment.gateway.webhook-secret=test-secret"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private PaymentProcessingJob paymentProcessingJob;
  @MockBean private PaymentGatewayClient gatewayClient;

  private String tokenFor(String username) {
    return "Bearer " + jwtTokenProvider.generateToken(username, Map.of("role", "CUSTOMER"));
  }

  /** Initiates a payment whose checkout session is {@code sessionId}; returns the payment id. */
  private Long initiate(String username, long orderId, String sessionId) throws Exception {
    // doReturn, not when(): the mock may currently be stubbed to throw.
    doReturn(
            new CheckoutSession(
                sessionId, "http://gw/checkout/" + sessionId, SessionStatus.OPEN, null))
        .when(gatewayClient)
        .createCheckoutSession(any());
    String body =
        mockMvc
            .perform(
                post("/payments")
                    .header("Authorization", tokenFor(username))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreatePaymentRequest(orderId, new BigDecimal("30.00"), "USD"))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status", is("PENDING")))
            .andExpect(jsonPath("$.checkoutUrl", is("http://gw/checkout/" + sessionId)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("id").asLong();
  }

  private void sendWebhook(String body, String signature, int expectedStatus) throws Exception {
    mockMvc
        .perform(
            post("/payments/webhooks/gateway")
                .contentType(MediaType.APPLICATION_JSON)
                .header(GatewayWebhookController.SIGNATURE_HEADER, signature)
                .content(body))
        .andExpect(status().is(expectedStatus));
  }

  private static String sign(String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }

  private void expectStatus(String username, Long paymentId, String status) throws Exception {
    mockMvc
        .perform(get("/payments/" + paymentId).header("Authorization", tokenFor(username)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is(status)));
  }

  @Test
  void paymentEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/payments/1")).andExpect(status().isUnauthorized());
  }

  @Test
  void signedWebhookCompletesPaymentThenRefundSettles() throws Exception {
    Long paymentId = initiate("alice", 500L, "cs_alice");

    // Re-initiating for the same orderId is idempotent and returns the same checkout.
    mockMvc
        .perform(
            post("/payments")
                .header("Authorization", tokenFor("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreatePaymentRequest(500L, new BigDecimal("30.00"), "USD"))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id", is(paymentId.intValue())))
        .andExpect(jsonPath("$.checkoutUrl", is("http://gw/checkout/cs_alice")));

    String event =
        """
        {"type":"checkout.session.completed","sessionId":"cs_alice","chargeReference":"ch_1"}""";
    sendWebhook(event, "sha256=forged", 401);
    expectStatus("alice", paymentId, "PENDING");

    sendWebhook(event, sign(event), 204);
    mockMvc
        .perform(get("/payments/" + paymentId).header("Authorization", tokenFor("alice")))
        .andExpect(jsonPath("$.status", is("COMPLETED")))
        .andExpect(jsonPath("$.checkoutUrl", nullValue()));

    // A duplicate or late outcome for a settled payment is ignored.
    String cancelled = """
        {"type":"checkout.session.cancelled","sessionId":"cs_alice"}""";
    sendWebhook(cancelled, sign(cancelled), 204);
    expectStatus("alice", paymentId, "COMPLETED");

    when(gatewayClient.refund("ch_1")).thenReturn(Optional.of("re_1"));
    mockMvc
        .perform(
            post("/payments/" + paymentId + "/refund")
                .header("Authorization", tokenFor("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefundRequest("changed my mind"))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status", is("PENDING")));
    paymentProcessingJob.processPendingRefunds();
    expectStatus("alice", paymentId, "REFUNDED");
  }

  @Test
  void sweepFailsPaymentWhoseCheckoutWasCancelledOrExpired() throws Exception {
    Long cancelledId = initiate("bob", 600L, "cs_bob_cancel");
    Long expiredId = initiate("bob", 601L, "cs_bob_expire");
    Long openId = initiate("bob", 602L, "cs_bob_open");
    when(gatewayClient.getCheckoutSession("cs_bob_cancel"))
        .thenReturn(
            Optional.of(new CheckoutSession("cs_bob_cancel", null, SessionStatus.CANCELLED, null)));
    when(gatewayClient.getCheckoutSession("cs_bob_expire"))
        .thenReturn(
            Optional.of(new CheckoutSession("cs_bob_expire", null, SessionStatus.EXPIRED, null)));
    when(gatewayClient.getCheckoutSession("cs_bob_open"))
        .thenReturn(
            Optional.of(new CheckoutSession("cs_bob_open", null, SessionStatus.OPEN, null)));

    paymentProcessingJob.processPendingPayments();

    mockMvc
        .perform(get("/payments/" + cancelledId).header("Authorization", tokenFor("bob")))
        .andExpect(jsonPath("$.status", is("FAILED")))
        .andExpect(jsonPath("$.failureReason", is("Payment cancelled by customer")));
    mockMvc
        .perform(get("/payments/" + expiredId).header("Authorization", tokenFor("bob")))
        .andExpect(jsonPath("$.status", is("FAILED")))
        .andExpect(jsonPath("$.failureReason", is("Payment window expired")));
    expectStatus("bob", openId, "PENDING");
  }

  @Test
  void gatewayOutageAtInitiationReturns502AndLeavesNoPayment() throws Exception {
    when(gatewayClient.createCheckoutSession(any()))
        .thenThrow(new PaymentGatewayException("down", new RuntimeException()));

    mockMvc
        .perform(
            post("/payments")
                .header("Authorization", tokenFor("erin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreatePaymentRequest(800L, new BigDecimal("5.00"), "USD"))))
        .andExpect(status().isBadGateway());

    // Rolled back: a retry for the same order opens a fresh checkout instead of a dead payment.
    initiate("erin", 800L, "cs_erin");
  }

  @Test
  void aUserCannotSeeAnotherUsersPayment() throws Exception {
    Long paymentId = initiate("carol", 700L, "cs_carol");

    mockMvc
        .perform(get("/payments/" + paymentId).header("Authorization", tokenFor("dave")))
        .andExpect(status().isNotFound());
  }
}
