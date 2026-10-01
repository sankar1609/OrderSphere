package com.ordersphere.orders.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.orders.client.InventoryClient;
import com.ordersphere.orders.client.PaymentClient;
import com.ordersphere.orders.client.ServiceTokenProvider;
import com.ordersphere.orders.client.ShippingClient;
import com.ordersphere.orders.domain.Compensation;
import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.dto.CreateOrderRequest;
import com.ordersphere.orders.exception.CompensationCallException;
import com.ordersphere.orders.exception.InventoryReservationException;
import com.ordersphere.orders.exception.PaymentInitiationException;
import com.ordersphere.orders.exception.ShipmentCreationException;
import com.ordersphere.orders.repository.CompensationRepository;
import com.ordersphere.orders.repository.OrderRepository;
import com.ordersphere.orders.service.OrderSagaProgressJob;
import com.ordersphere.security.testing.TestJwtIssuer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
@SpringBootTest(properties = "eureka.client.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private OrderSagaProgressJob orderSagaProgressJob;
  @Autowired private CompensationRepository compensationRepository;
  @Autowired private OrderRepository orderRepository;

  @MockBean private InventoryClient inventoryClient;
  @MockBean private PaymentClient paymentClient;
  @MockBean private ShippingClient shippingClient;
  @MockBean private ServiceTokenProvider serviceTokenProvider;

  @BeforeEach
  void stubPricedReservation() {
    when(serviceTokenProvider.bearerToken()).thenReturn("Bearer test-service-token");
    // By default Inventory reserves everything requested at 10.00 per unit.
    when(inventoryClient.reserve(any(), any(), anyString()))
        .thenAnswer(
            invocation -> {
              List<InventoryClient.ReserveRequest.Item> items = invocation.getArgument(1);
              return new InventoryClient.ReserveResponse(
                  items.stream()
                      .map(
                          item ->
                              new InventoryClient.ReserveResponse.LineItem(
                                  item.sku(), item.quantity(), new BigDecimal("10.00")))
                      .toList());
            });
  }

  private String tokenFor(String username) {
    return TestJwtIssuer.token(username, "CUSTOMER");
  }

  private CreateOrderRequest requestFor(String sku) {
    return new CreateOrderRequest(
        List.of(new CreateOrderRequest.Item(sku, 2)), "USD", "1 Test Way");
  }

  @Test
  void healthProbesAreOpenButCircuitBreakerDetailsAreAdminOnly() throws Exception {
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    mockMvc
        .perform(
            get("/actuator/circuitbreakers")
                .header("Authorization", TestJwtIssuer.bearer("alice", "CUSTOMER")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/actuator/circuitbreakers")
                .header("Authorization", TestJwtIssuer.bearer("admin", "ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void ordersEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void createOrderAwaitsPaymentWhenReservationAndInitiationSucceed() throws Exception {
    when(paymentClient.initiate(any(), anyString(), any(), eq("USD"), anyString()))
        .thenReturn(new PaymentClient.InitiatedPayment(42L, "http://gw/checkout/cs_42"));

    mockMvc
        .perform(
            post("/orders")
                .header("Authorization", "Bearer " + tokenFor("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestFor("SKU-1"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status", is("AWAITING_PAYMENT")))
        .andExpect(jsonPath("$.paymentId", is(42)))
        .andExpect(jsonPath("$.totalAmount", is(20.0)))
        .andExpect(jsonPath("$.currency", is("USD")))
        .andExpect(jsonPath("$.customerUsername", is("alice")));
  }

  @Test
  void createOrderCancelsWhenInventoryRejectsReservation() throws Exception {
    doThrow(
            new InventoryReservationException(
                "Inventory reservation failed with status 409",
                null,
                "Not enough stock: SKU-EMPTY (2 requested, 0 available)"))
        .when(inventoryClient)
        .reserve(any(), any(), anyString());

    mockMvc
        .perform(
            post("/orders")
                .header("Authorization", "Bearer " + tokenFor("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestFor("SKU-EMPTY"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status", is("CANCELLED")))
        .andExpect(
            jsonPath(
                "$.cancellationReason",
                is("Not enough stock: SKU-EMPTY (2 requested, 0 available)")))
        .andExpect(jsonPath("$.checkoutUrl").doesNotExist());
  }

  @Test
  void createOrderCompensatesInventoryWhenPaymentInitiationFails() throws Exception {
    doThrow(new PaymentInitiationException("payment provider unavailable"))
        .when(paymentClient)
        .initiate(any(), anyString(), any(), any(), anyString());

    mockMvc
        .perform(
            post("/orders")
                .header("Authorization", "Bearer " + tokenFor("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestFor("SKU-2"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status", is("CANCELLED")));

    verify(inventoryClient).release(anyLong(), anyString());
  }

  @Test
  void fullSagaConfirmsOrderAndCreatesShipmentOncePaymentCompletes() throws Exception {
    when(paymentClient.initiate(any(), anyString(), any(), any(), anyString()))
        .thenReturn(new PaymentClient.InitiatedPayment(42L, "http://gw/checkout/cs_42"));
    when(paymentClient.getStatus(eq(42L), anyString()))
        .thenReturn(PaymentClient.PaymentStatus.COMPLETED);
    when(shippingClient.createShipment(any(), any(), any(), anyString())).thenReturn(7L);

    String createdBody =
        mockMvc
            .perform(
                post("/orders")
                    .header("Authorization", "Bearer " + tokenFor("bob"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestFor("SKU-3"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("AWAITING_PAYMENT")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long orderId = objectMapper.readTree(createdBody).get("id").asLong();

    orderSagaProgressJob.progressAwaitingPaymentOrders();

    mockMvc
        .perform(get("/orders/" + orderId).header("Authorization", "Bearer " + tokenFor("bob")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("CONFIRMED")))
        .andExpect(jsonPath("$.shipmentId", is(7)));
  }

  @Test
  void sagaCancelsOrderAndReleasesInventoryWhenPaymentFails() throws Exception {
    when(paymentClient.initiate(any(), anyString(), any(), any(), anyString()))
        .thenReturn(new PaymentClient.InitiatedPayment(99L, "http://gw/checkout/cs_99"));
    when(paymentClient.getStatus(eq(99L), anyString()))
        .thenReturn(PaymentClient.PaymentStatus.FAILED);

    String createdBody =
        mockMvc
            .perform(
                post("/orders")
                    .header("Authorization", "Bearer " + tokenFor("carol"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestFor("SKU-4"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long orderId = objectMapper.readTree(createdBody).get("id").asLong();

    orderSagaProgressJob.progressAwaitingPaymentOrders();

    mockMvc
        .perform(get("/orders/" + orderId).header("Authorization", "Bearer " + tokenFor("carol")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("CANCELLED")));
    verify(inventoryClient).release(eq(orderId), anyString());
    verify(shippingClient, org.mockito.Mockito.never()).createShipment(any(), any(), any(), any());
  }

  @Test
  void refundThatFailsTransientlyIsRecordedAndRetriedUntilItSucceeds() throws Exception {
    when(paymentClient.initiate(any(), anyString(), any(), any(), anyString()))
        .thenReturn(new PaymentClient.InitiatedPayment(77L, "http://gw/checkout/cs_77"));
    when(paymentClient.getStatus(eq(77L), anyString()))
        .thenReturn(PaymentClient.PaymentStatus.COMPLETED);
    when(shippingClient.createShipment(any(), any(), any(), anyString())).thenReturn(500L);
    when(shippingClient.getStatus(eq(500L), anyString()))
        .thenReturn(ShippingClient.ShipmentStatus.CREATED);
    // payment-service can't verify the service token for a moment (e.g. JWKS not fetched yet),
    // then recovers.
    doThrow(
            new CompensationCallException(
                "Refund of paymentId 77 failed with HTTP 401", null, true, 401))
        .doNothing()
        .when(paymentClient)
        .refund(eq(77L), anyString(), anyString());

    String createdBody =
        mockMvc
            .perform(
                post("/orders")
                    .header("Authorization", "Bearer " + tokenFor("dora"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestFor("SKU-8"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long orderId = objectMapper.readTree(createdBody).get("id").asLong();
    orderSagaProgressJob.progressAwaitingPaymentOrders(); // payment COMPLETED -> CONFIRMED

    mockMvc
        .perform(
            post("/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + tokenFor("dora")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("CANCELLED")));

    // The immediate attempt failed, but the refund is recorded rather than lost.
    Compensation refund = refundFor(orderId);
    assertThat(refund.getStatus()).isEqualTo(Compensation.Status.PENDING);
    assertThat(refund.getAttempts()).isEqualTo(1);
    assertThat(refund.getLastError()).contains("401");

    // Next due sweep: payment-service is back and the refund goes through.
    refund.setNextAttemptAt(Instant.now().minusSeconds(1));
    compensationRepository.save(refund);
    orderSagaProgressJob.sweep();

    Compensation retried = refundFor(orderId);
    assertThat(retried.getStatus()).isEqualTo(Compensation.Status.DONE);
    assertThat(retried.getAttempts()).isEqualTo(2);
    verify(paymentClient, org.mockito.Mockito.times(2))
        .refund(eq(77L), eq("Order cancelled by customer"), anyString());
  }

  @Test
  void adminsCanListAndRetryFailedCompensations() throws Exception {
    Compensation stuck =
        new Compensation(
            Compensation.Type.REFUND_PAYMENT,
            9001L,
            55L,
            "Order cancelled by customer",
            Instant.now());
    stuck.setStatus(Compensation.Status.FAILED);
    stuck.setAttempts(20);
    stuck.setLastError("Refund of paymentId 55 failed with HTTP 503");
    Long id = compensationRepository.save(stuck).getId();
    String admin = "Bearer " + TestJwtIssuer.token("admin", "ADMIN");

    mockMvc
        .perform(
            get("/orders/admin/compensations")
                .header("Authorization", "Bearer " + tokenFor("erin")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get("/orders/admin/compensations").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + id + ")].orderId").value(9001))
        .andExpect(jsonPath("$[?(@.id == " + id + ")].lastError").value(stuck.getLastError()));
    mockMvc
        .perform(
            get("/orders/admin/compensations")
                .param("status", "PENDING")
                .header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());

    // Payment-service is healthy again: the retry goes straight through.
    mockMvc
        .perform(
            post("/orders/admin/compensations/" + id + "/retry").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("DONE")))
        .andExpect(jsonPath("$.attempts", is(1)));
    verify(paymentClient).refund(eq(55L), eq("Order cancelled by customer"), anyString());

    mockMvc
        .perform(
            post("/orders/admin/compensations/" + id + "/retry").header("Authorization", admin))
        .andExpect(status().isConflict());
    mockMvc
        .perform(post("/orders/admin/compensations/999999/retry").header("Authorization", admin))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/orders/admin/compensations")
                .param("status", "BOGUS")
                .header("Authorization", admin))
        .andExpect(status().isBadRequest());
  }

  private Long paidOrderFor(String username, long paymentId) throws Exception {
    when(paymentClient.initiate(any(), anyString(), any(), any(), anyString()))
        .thenReturn(
            new PaymentClient.InitiatedPayment(paymentId, "http://gw/checkout/cs_" + paymentId));
    when(paymentClient.getStatus(eq(paymentId), anyString()))
        .thenReturn(PaymentClient.PaymentStatus.COMPLETED);
    String createdBody =
        mockMvc
            .perform(
                post("/orders")
                    .header("Authorization", "Bearer " + tokenFor(username))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestFor("SKU-9"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long orderId = objectMapper.readTree(createdBody).get("id").asLong();
    orderSagaProgressJob.progressAwaitingPaymentOrders(); // payment COMPLETED -> CONFIRMED
    return orderId;
  }

  @Test
  void shipmentThatCantBeCreatedYetIsRetriedUntilItIs() throws Exception {
    // shipping-service is down when the order is paid, then comes back.
    when(shippingClient.createShipment(any(), any(), any(), anyString()))
        .thenThrow(new ShipmentCreationException("Shipping service unreachable"))
        .thenReturn(600L);
    Long orderId = paidOrderFor("fred", 88L);
    String admin = "Bearer " + TestJwtIssuer.token("admin", "ADMIN");

    // Paid, so confirmed - but without a shipment, and scheduled for a retry.
    mockMvc
        .perform(get("/orders/" + orderId).header("Authorization", "Bearer " + tokenFor("fred")))
        .andExpect(jsonPath("$.status", is("CONFIRMED")))
        .andExpect(jsonPath("$.shipmentId").doesNotExist());
    mockMvc
        .perform(get("/orders/admin/unshipped").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.orderId == " + orderId + ")].retrying").value(true))
        .andExpect(jsonPath("$[?(@.orderId == " + orderId + ")].shipmentAttempts").value(1));

    // Next due sweep: the shipment is created.
    Order order = orderRepository.findById(orderId).orElseThrow();
    order.setShipmentNextAttemptAt(Instant.now().minusSeconds(1));
    orderRepository.save(order);
    orderSagaProgressJob.sweep();

    mockMvc
        .perform(get("/orders/" + orderId).header("Authorization", "Bearer " + tokenFor("fred")))
        .andExpect(jsonPath("$.status", is("CONFIRMED")))
        .andExpect(jsonPath("$.shipmentId", is(600)));
    mockMvc
        .perform(get("/orders/admin/unshipped").header("Authorization", admin))
        .andExpect(jsonPath("$[?(@.orderId == " + orderId + ")]").isEmpty());
    verify(shippingClient, org.mockito.Mockito.times(2))
        .createShipment(eq(orderId), eq("fred"), eq("1 Test Way"), anyString());
  }

  @Test
  void adminsCanRetryAShipmentShippingRejected() throws Exception {
    when(shippingClient.createShipment(any(), any(), any(), anyString()))
        .thenThrow(new ShipmentCreationException("HTTP 400", null, false))
        .thenReturn(601L);
    Long orderId = paidOrderFor("gina", 89L);
    String admin = "Bearer " + TestJwtIssuer.token("admin", "ADMIN");

    mockMvc
        .perform(
            get("/orders/admin/unshipped").header("Authorization", "Bearer " + tokenFor("gina")))
        .andExpect(status().isForbidden());
    // Rejected, so the sweep has given up on it: it needs an admin.
    mockMvc
        .perform(get("/orders/admin/unshipped").header("Authorization", admin))
        .andExpect(jsonPath("$[?(@.orderId == " + orderId + ")].retrying").value(false))
        .andExpect(jsonPath("$[?(@.orderId == " + orderId + ")].lastError").value("HTTP 400"));
    orderSagaProgressJob.sweep();
    verify(shippingClient, org.mockito.Mockito.times(1))
        .createShipment(eq(orderId), any(), any(), anyString());

    // Fixed on the shipping side: the admin retry creates it.
    mockMvc
        .perform(
            post("/orders/admin/unshipped/" + orderId + "/retry").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shipmentId", is(601)))
        .andExpect(jsonPath("$.retrying", is(false)))
        .andExpect(jsonPath("$.lastError").doesNotExist());

    mockMvc
        .perform(
            post("/orders/admin/unshipped/" + orderId + "/retry").header("Authorization", admin))
        .andExpect(status().isConflict());
    mockMvc
        .perform(post("/orders/admin/unshipped/999999/retry").header("Authorization", admin))
        .andExpect(status().isNotFound());
  }

  private Compensation refundFor(Long orderId) {
    return compensationRepository.findByOrderIdOrderById(orderId).stream()
        .filter(c -> c.getType() == Compensation.Type.REFUND_PAYMENT)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void cancelOrderIsIdempotentAndReleasesInventory() throws Exception {
    when(paymentClient.initiate(any(), anyString(), any(), any(), anyString()))
        .thenReturn(new PaymentClient.InitiatedPayment(11L, "http://gw/checkout/cs_11"));

    String createdBody =
        mockMvc
            .perform(
                post("/orders")
                    .header("Authorization", "Bearer " + tokenFor("dave"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestFor("SKU-5"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long orderId = objectMapper.readTree(createdBody).get("id").asLong();

    mockMvc
        .perform(
            post("/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + tokenFor("dave")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("CANCELLED")));
    verify(inventoryClient).release(anyLong(), anyString());

    mockMvc
        .perform(
            post("/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + tokenFor("dave")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("CANCELLED")));
  }

  @Test
  void aUserCannotSeeOrCancelAnotherUsersOrder() throws Exception {
    when(paymentClient.initiate(any(), anyString(), any(), any(), anyString()))
        .thenReturn(new PaymentClient.InitiatedPayment(13L, "http://gw/checkout/cs_13"));

    String createdBody =
        mockMvc
            .perform(
                post("/orders")
                    .header("Authorization", "Bearer " + tokenFor("erin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(requestFor("SKU-6"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long orderId = objectMapper.readTree(createdBody).get("id").asLong();

    mockMvc
        .perform(get("/orders/" + orderId).header("Authorization", "Bearer " + tokenFor("frank")))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + tokenFor("frank")))
        .andExpect(status().isNotFound());
  }
}
