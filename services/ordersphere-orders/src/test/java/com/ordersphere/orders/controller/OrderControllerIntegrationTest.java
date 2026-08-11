package com.ordersphere.orders.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.orders.client.InventoryClient;
import com.ordersphere.orders.dto.CreateOrderRequest;
import com.ordersphere.orders.exception.InventoryReservationException;
import com.ordersphere.security.JwtTokenProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider jwtTokenProvider;

  @MockBean private InventoryClient inventoryClient;

  private String tokenFor(String username) {
    return jwtTokenProvider.generateToken(username, Map.of("role", "CUSTOMER"));
  }

  @Test
  void ordersEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void createOrderConfirmsWhenInventoryReservationSucceeds() throws Exception {
    CreateOrderRequest request =
        new CreateOrderRequest(List.of(new CreateOrderRequest.Item("SKU-1", 2)));

    mockMvc
        .perform(
            post("/orders")
                .header("Authorization", "Bearer " + tokenFor("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status", is("CONFIRMED")))
        .andExpect(jsonPath("$.customerUsername", is("alice")));
  }

  @Test
  void createOrderCancelsWhenInventoryRejectsReservation() throws Exception {
    doThrow(new InventoryReservationException("no such sku"))
        .when(inventoryClient)
        .reserve(any(), any(), anyString());

    CreateOrderRequest request =
        new CreateOrderRequest(List.of(new CreateOrderRequest.Item("SKU-UNKNOWN", 1)));

    mockMvc
        .perform(
            post("/orders")
                .header("Authorization", "Bearer " + tokenFor("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status", is("CANCELLED")));
  }

  @Test
  void cancelOrderIsIdempotentAndReleasesInventory() throws Exception {
    CreateOrderRequest request =
        new CreateOrderRequest(List.of(new CreateOrderRequest.Item("SKU-2", 1)));

    String createdBody =
        mockMvc
            .perform(
                post("/orders")
                    .header("Authorization", "Bearer " + tokenFor("bob"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long orderId = objectMapper.readTree(createdBody).get("id").asLong();

    mockMvc
        .perform(
            post("/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + tokenFor("bob")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("CANCELLED")));
    verify(inventoryClient).release(anyLong(), anyString());

    mockMvc
        .perform(
            post("/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + tokenFor("bob")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("CANCELLED")));
  }

  @Test
  void aUserCannotSeeOrCancelAnotherUsersOrder() throws Exception {
    CreateOrderRequest request =
        new CreateOrderRequest(List.of(new CreateOrderRequest.Item("SKU-3", 1)));

    String createdBody =
        mockMvc
            .perform(
                post("/orders")
                    .header("Authorization", "Bearer " + tokenFor("carol"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long orderId = objectMapper.readTree(createdBody).get("id").asLong();

    mockMvc
        .perform(get("/orders/" + orderId).header("Authorization", "Bearer " + tokenFor("dave")))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/orders/" + orderId + "/cancel")
                .header("Authorization", "Bearer " + tokenFor("dave")))
        .andExpect(status().isNotFound());
  }
}
