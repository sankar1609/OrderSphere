package com.ordersphere.inventory.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.inventory.dto.CreateProductRequest;
import com.ordersphere.inventory.dto.ReserveStockRequest;
import com.ordersphere.inventory.dto.RestockRequest;
import com.ordersphere.security.JwtTokenProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class InventoryControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider jwtTokenProvider;

  private String adminToken() {
    return jwtTokenProvider.generateToken("test-admin", Map.of("role", "ADMIN"));
  }

  private String customerToken() {
    return jwtTokenProvider.generateToken("test-customer", Map.of("role", "CUSTOMER"));
  }

  @Test
  void catalogEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/inventory/products")).andExpect(status().isUnauthorized());
  }

  @Test
  void nonAdminCannotCreateProducts() throws Exception {
    CreateProductRequest request = new CreateProductRequest("SKU-FORBIDDEN", "Widget", 5, 1);

    mockMvc
        .perform(
            post("/inventory/products")
                .header("Authorization", "Bearer " + customerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void duplicateSkuIsRejected() throws Exception {
    CreateProductRequest request = new CreateProductRequest("SKU-DUP", "Widget", 5, 1);
    createProduct(request);

    mockMvc
        .perform(
            post("/inventory/products")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  @Test
  void fullReserveIdempotentReleaseBackorderRestockAndConfirmFlow() throws Exception {
    createProduct(new CreateProductRequest("SKU-FLOW", "Widget", 10, 3));

    ReserveStockRequest firstReserve =
        new ReserveStockRequest(100L, List.of(new ReserveStockRequest.Item("SKU-FLOW", 8)));
    mockMvc
        .perform(
            post("/inventory/reservations")
                .header("Authorization", "Bearer " + customerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstReserve)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reserved[0].sku", is("SKU-FLOW")))
        .andExpect(jsonPath("$.reserved[0].quantity", is(8)))
        .andExpect(jsonPath("$.backordered", org.hamcrest.Matchers.hasSize(0)));

    // Re-reserving the same orderId must be idempotent, not double-reserve.
    mockMvc
        .perform(
            post("/inventory/reservations")
                .header("Authorization", "Bearer " + customerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstReserve)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reserved[0].quantity", is(8)));

    mockMvc
        .perform(
            get("/inventory/products/SKU-FLOW")
                .header("Authorization", "Bearer " + customerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantityReserved", is(8)))
        .andExpect(jsonPath("$.availableQuantity", is(2)));

    // Release restores stock.
    mockMvc
        .perform(
            post("/inventory/reservations/100/release")
                .header("Authorization", "Bearer " + customerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("RELEASED")));

    mockMvc
        .perform(
            get("/inventory/products/SKU-FLOW")
                .header("Authorization", "Bearer " + customerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availableQuantity", is(10)));

    // Reserve more than available: partial fulfillment + backorder for the shortfall.
    ReserveStockRequest overReserve =
        new ReserveStockRequest(200L, List.of(new ReserveStockRequest.Item("SKU-FLOW", 15)));
    mockMvc
        .perform(
            post("/inventory/reservations")
                .header("Authorization", "Bearer " + customerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overReserve)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reserved[0].quantity", is(10)))
        .andExpect(jsonPath("$.backordered[0].quantity", is(5)));

    // Restocking should auto-fulfill the open backorder.
    mockMvc
        .perform(
            post("/inventory/products/SKU-FLOW/restock")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RestockRequest(20))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantityOnHand", is(30)))
        .andExpect(jsonPath("$.quantityReserved", is(15)))
        .andExpect(jsonPath("$.availableQuantity", is(15)));

    // Confirming locks the hold in (clears expiresAt so it's excluded from the expiry sweep).
    mockMvc
        .perform(
            post("/inventory/reservations/200/confirm")
                .header("Authorization", "Bearer " + customerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("CONFIRMED")))
        .andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.nullValue()));
  }

  private void createProduct(CreateProductRequest request) throws Exception {
    mockMvc
        .perform(
            post("/inventory/products")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }
}
