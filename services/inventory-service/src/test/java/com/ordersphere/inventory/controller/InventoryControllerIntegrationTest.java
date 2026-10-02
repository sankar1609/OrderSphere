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
import com.ordersphere.security.testing.TestJwtIssuer;
import java.math.BigDecimal;
import java.util.List;
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
class InventoryControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private String adminToken() {
    return TestJwtIssuer.token("test-admin", "ADMIN");
  }

  private String customerToken() {
    return TestJwtIssuer.token("test-customer", "CUSTOMER");
  }

  /** The identity ordersphere-orders drives reservations with. */
  private String serviceToken() {
    return TestJwtIssuer.token("orders-service", "SERVICE");
  }

  @Test
  void customersCannotReserveConfirmOrReleaseStock() throws Exception {
    mockMvc
        .perform(
            post("/inventory/reservations")
                .header("Authorization", "Bearer " + customerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\": 999, \"items\": [{\"sku\": \"ANY\", \"quantity\": 1}]}"))
        .andExpect(status().isForbidden());
    for (String action : new String[] {"confirm", "release"}) {
      mockMvc
          .perform(
              post("/inventory/reservations/999/" + action)
                  .header("Authorization", "Bearer " + customerToken()))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void catalogEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/inventory/products")).andExpect(status().isUnauthorized());
  }

  @Test
  void nonAdminCannotCreateProducts() throws Exception {
    CreateProductRequest request =
        new CreateProductRequest("SKU-FORBIDDEN", "Widget", 5, 1, new BigDecimal("9.99"));

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
    CreateProductRequest request =
        new CreateProductRequest("SKU-DUP", "Widget", 5, 1, new BigDecimal("9.99"));
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
  void fullReserveIdempotentReleaseRejectRestockAndConfirmFlow() throws Exception {
    createProduct(new CreateProductRequest("SKU-FLOW", "Widget", 10, 3, new BigDecimal("9.99")));

    ReserveStockRequest firstReserve =
        new ReserveStockRequest(100L, List.of(new ReserveStockRequest.Item("SKU-FLOW", 8)));
    mockMvc
        .perform(
            post("/inventory/reservations")
                .header("Authorization", "Bearer " + serviceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstReserve)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reserved[0].sku", is("SKU-FLOW")))
        .andExpect(jsonPath("$.reserved[0].quantity", is(8)))
        .andExpect(jsonPath("$.reserved[0].unitPrice", is(9.99)));

    // Re-reserving the same orderId must be idempotent, not double-reserve.
    mockMvc
        .perform(
            post("/inventory/reservations")
                .header("Authorization", "Bearer " + serviceToken())
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
        .andExpect(jsonPath("$.availableQuantity", is(2)))
        .andExpect(jsonPath("$.unitPrice", is(9.99)));

    // Release restores stock.
    mockMvc
        .perform(
            post("/inventory/reservations/100/release")
                .header("Authorization", "Bearer " + serviceToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("RELEASED")));

    mockMvc
        .perform(
            get("/inventory/products/SKU-FLOW")
                .header("Authorization", "Bearer " + customerToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availableQuantity", is(10)));

    // Asking for more than is available is rejected outright - nothing is held.
    ReserveStockRequest overReserve =
        new ReserveStockRequest(200L, List.of(new ReserveStockRequest.Item("SKU-FLOW", 15)));
    mockMvc
        .perform(
            post("/inventory/reservations")
                .header("Authorization", "Bearer " + serviceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overReserve)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message", is("Not enough stock: SKU-FLOW (15 requested, 10 available)")));
    mockMvc
        .perform(
            get("/inventory/products/SKU-FLOW")
                .header("Authorization", "Bearer " + customerToken()))
        .andExpect(jsonPath("$.availableQuantity", is(10)));

    // After a restock the same order fits.
    mockMvc
        .perform(
            post("/inventory/products/SKU-FLOW/restock")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RestockRequest(20))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantityOnHand", is(30)))
        .andExpect(jsonPath("$.quantityReserved", is(0)))
        .andExpect(jsonPath("$.availableQuantity", is(30)));
    mockMvc
        .perform(
            post("/inventory/reservations")
                .header("Authorization", "Bearer " + serviceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overReserve)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reserved[0].quantity", is(15)));

    // Confirming locks the hold in (clears expiresAt so it's excluded from the expiry sweep).
    mockMvc
        .perform(
            post("/inventory/reservations/200/confirm")
                .header("Authorization", "Bearer " + serviceToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("CONFIRMED")))
        .andExpect(jsonPath("$.expiresAt").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void quantitiesAndTextBeyondTheLimitsAreRejectedNotStored() throws Exception {
    createProduct(new CreateProductRequest("SKU-LIM", "Widget", 5, 0, new BigDecimal("2.00")));

    // Two lines whose sum would wrap an int negative: rejected by validation, nothing reserved.
    mockMvc
        .perform(
            post("/inventory/reservations")
                .header("Authorization", "Bearer " + serviceToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ReserveStockRequest(
                            300L,
                            List.of(
                                new ReserveStockRequest.Item("SKU-LIM", Integer.MAX_VALUE),
                                new ReserveStockRequest.Item("SKU-LIM", Integer.MAX_VALUE))))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/inventory/products/SKU-LIM/restock")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RestockRequest(Integer.MAX_VALUE))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get("/inventory/products/SKU-LIM").header("Authorization", "Bearer " + customerToken()))
        .andExpect(jsonPath("$.quantityOnHand", is(5)))
        .andExpect(jsonPath("$.quantityReserved", is(0)));

    for (String badSku : List.of("A/B", "A B", "x".repeat(65))) {
      mockMvc
          .perform(
              post("/inventory/products")
                  .header("Authorization", "Bearer " + adminToken())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new CreateProductRequest(badSku, "Widget", 1, 0, BigDecimal.ONE))))
          .andExpect(status().isBadRequest());
    }
    mockMvc
        .perform(
            post("/inventory/products")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateProductRequest(
                            "SKU-LONG", "n".repeat(256), 1, 0, BigDecimal.ONE))))
        .andExpect(status().isBadRequest());
    // Payments start at 0.01, so a product priced 0.00 could never be bought.
    mockMvc
        .perform(
            post("/inventory/products")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateProductRequest("SKU-FREE", "Widget", 1, 0, BigDecimal.ZERO))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void vendorsRestockOnlyTheirOwnProducts() throws Exception {
    String owner = TestJwtIssuer.token("vendor-one", "VENDOR");
    String other = TestJwtIssuer.token("vendor-two", "VENDOR");
    mockMvc
        .perform(
            post("/inventory/products")
                .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateProductRequest("SKU-OWN", "Widget", 1, 0, BigDecimal.ONE))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.createdBy", is("vendor-one")));

    mockMvc
        .perform(
            post("/inventory/products/SKU-OWN/restock")
                .header("Authorization", "Bearer " + other)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RestockRequest(5))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/inventory/products/SKU-OWN/restock")
                .header("Authorization", "Bearer " + owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RestockRequest(5))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantityOnHand", is(6)));
  }

  private void createProduct(CreateProductRequest request) throws Exception {
    mockMvc
        .perform(
            post("/inventory/products")
                .header("Authorization", "Bearer " + adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.createdBy").isNotEmpty());
  }
}
