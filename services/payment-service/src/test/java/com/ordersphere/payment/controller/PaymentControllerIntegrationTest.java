package com.ordersphere.payment.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.payment.domain.PaymentMethodType;
import com.ordersphere.payment.dto.CreatePaymentMethodRequest;
import com.ordersphere.payment.dto.CreatePaymentRequest;
import com.ordersphere.payment.dto.RefundRequest;
import com.ordersphere.payment.service.PaymentProcessingJob;
import com.ordersphere.security.JwtTokenProvider;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class PaymentControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private PaymentProcessingJob paymentProcessingJob;

  private String tokenFor(String username) {
    return jwtTokenProvider.generateToken(username, Map.of("role", "CUSTOMER"));
  }

  private Long createPaymentMethod(String username, String token) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/payment-methods")
                    .header("Authorization", "Bearer " + tokenFor(username))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreatePaymentMethodRequest(PaymentMethodType.CARD, token))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("id").asLong();
  }

  @Test
  void paymentEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/payments/1")).andExpect(status().isUnauthorized());
  }

  @Test
  void fullInitiateSettleAndRefundFlow() throws Exception {
    Long methodId = createPaymentMethod("alice", "tok-good");

    String createdBody =
        mockMvc
            .perform(
                post("/payments")
                    .header("Authorization", "Bearer " + tokenFor("alice"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreatePaymentRequest(
                                500L, methodId, new BigDecimal("30.00"), "USD"))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status", is("PENDING")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long paymentId = objectMapper.readTree(createdBody).get("id").asLong();

    // Re-initiating for the same orderId is idempotent.
    mockMvc
        .perform(
            post("/payments")
                .header("Authorization", "Bearer " + tokenFor("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreatePaymentRequest(500L, methodId, new BigDecimal("30.00"), "USD"))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.id", is(paymentId.intValue())));

    paymentProcessingJob.processPendingPayments();

    mockMvc
        .perform(
            get("/payments/" + paymentId).header("Authorization", "Bearer " + tokenFor("alice")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("COMPLETED")));

    mockMvc
        .perform(
            post("/payments/" + paymentId + "/refund")
                .header("Authorization", "Bearer " + tokenFor("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefundRequest("changed my mind"))))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.status", is("PENDING")));

    paymentProcessingJob.processPendingRefunds();

    mockMvc
        .perform(
            get("/payments/" + paymentId).header("Authorization", "Bearer " + tokenFor("alice")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("REFUNDED")));
  }

  @Test
  void paymentDeclinedByGatewayEndsFailed() throws Exception {
    Long methodId = createPaymentMethod("bob", "FAIL-DECLINE");

    String createdBody =
        mockMvc
            .perform(
                post("/payments")
                    .header("Authorization", "Bearer " + tokenFor("bob"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreatePaymentRequest(
                                600L, methodId, new BigDecimal("10.00"), "USD"))))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long paymentId = objectMapper.readTree(createdBody).get("id").asLong();

    paymentProcessingJob.processPendingPayments();

    mockMvc
        .perform(get("/payments/" + paymentId).header("Authorization", "Bearer " + tokenFor("bob")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("FAILED")));
  }

  @Test
  void aUserCannotSeeAnotherUsersPayment() throws Exception {
    Long methodId = createPaymentMethod("carol", "tok-good");

    String createdBody =
        mockMvc
            .perform(
                post("/payments")
                    .header("Authorization", "Bearer " + tokenFor("carol"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreatePaymentRequest(
                                700L, methodId, new BigDecimal("15.00"), "USD"))))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long paymentId = objectMapper.readTree(createdBody).get("id").asLong();

    mockMvc
        .perform(
            get("/payments/" + paymentId).header("Authorization", "Bearer " + tokenFor("dave")))
        .andExpect(status().isNotFound());
  }
}
