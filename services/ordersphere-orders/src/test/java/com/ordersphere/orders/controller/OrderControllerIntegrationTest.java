package com.ordersphere.orders.controller;

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
import com.ordersphere.orders.client.ShippingClient;
import com.ordersphere.orders.dto.CreateOrderRequest;
import com.ordersphere.orders.exception.InventoryReservationException;
import com.ordersphere.orders.exception.PaymentInitiationException;
import com.ordersphere.orders.service.OrderSagaProgressJob;
import com.ordersphere.security.JwtTokenProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private OrderSagaProgressJob orderSagaProgressJob;

  @MockBean private InventoryClient inventoryClient;
  @MockBean private PaymentClient paymentClient;
  @MockBean private ShippingClient shippingClient;

  @BeforeEach
  void stubPricedReservation() {
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
                      .toList(),
                  List.of());
            });
  }

  private String tokenFor(String username) {
    return jwtTokenProvider.generateToken(username, Map.of("role", "CUSTOMER"));
  }

  private CreateOrderRequest requestFor(String sku) {
    return new CreateOrderRequest(
        List.of(new CreateOrderRequest.Item(sku, 2)), 5L, "USD", "1 Test Way");
  }

  @Test
  void ordersEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void createOrderAwaitsPaymentWhenReservationAndInitiationSucceed() throws Exception {
    when(paymentClient.initiate(any(), eq(5L), any(), eq("USD"), anyString())).thenReturn(42L);

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
    doThrow(new InventoryReservationException("no such sku"))
        .when(inventoryClient)
        .reserve(any(), any(), anyString());

    mockMvc
        .perform(
            post("/orders")
                .header("Authorization", "Bearer " + tokenFor("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestFor("SKU-UNKNOWN"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status", is("CANCELLED")));
  }

  @Test
  void createOrderCompensatesInventoryWhenPaymentInitiationFails() throws Exception {
    doThrow(new PaymentInitiationException("no such payment method"))
        .when(paymentClient)
        .initiate(any(), any(), any(), any(), anyString());

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
    when(paymentClient.initiate(any(), any(), any(), any(), anyString())).thenReturn(42L);
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
    when(paymentClient.initiate(any(), any(), any(), any(), anyString())).thenReturn(99L);
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
  void cancelOrderIsIdempotentAndReleasesInventory() throws Exception {
    when(paymentClient.initiate(any(), any(), any(), any(), anyString())).thenReturn(11L);

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
    when(paymentClient.initiate(any(), any(), any(), any(), anyString())).thenReturn(13L);

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
