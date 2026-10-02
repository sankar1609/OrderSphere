package com.ordersphere.shipping.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.security.testing.TestJwtIssuer;
import com.ordersphere.shipping.dto.CreateShipmentRequest;
import com.ordersphere.shipping.dto.ReturnShipmentRequest;
import com.ordersphere.shipping.service.ShipmentProgressJob;
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
class ShippingControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ShipmentProgressJob shipmentProgressJob;

  private String tokenFor(String username, String role) {
    return TestJwtIssuer.token(username, role);
  }

  @Test
  void shipmentEndpointsRequireAuthentication() throws Exception {
    mockMvc.perform(get("/shipments/1")).andExpect(status().isUnauthorized());
  }

  @Test
  void nonAdminCannotCreateShipment() throws Exception {
    mockMvc
        .perform(
            post("/shipments")
                .header("Authorization", "Bearer " + tokenFor("alice", "CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateShipmentRequest(900L, "alice", "1 Test Way"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void fullCreateProgressAndReturnFlow() throws Exception {
    String createdBody =
        mockMvc
            .perform(
                post("/shipments")
                    .header("Authorization", "Bearer " + tokenFor("admin", "ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateShipmentRequest(900L, "alice", "1 Test Way"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("CREATED")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long shipmentId = objectMapper.readTree(createdBody).get("id").asLong();

    shipmentProgressJob.advancePendingShipments();
    shipmentProgressJob.advancePendingShipments();
    shipmentProgressJob.advancePendingShipments();

    mockMvc
        .perform(
            get("/shipments/" + shipmentId)
                .header("Authorization", "Bearer " + tokenFor("alice", "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("DELIVERED")));

    mockMvc
        .perform(
            get("/shipments/" + shipmentId + "/tracking")
                .header("Authorization", "Bearer " + tokenFor("alice", "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(4)))
        .andExpect(jsonPath("$[0].status", is("CREATED")))
        .andExpect(jsonPath("$[3].status", is("DELIVERED")));

    String returnBody =
        mockMvc
            .perform(
                post("/shipments/" + shipmentId + "/return")
                    .header("Authorization", "Bearer " + tokenFor("alice", "CUSTOMER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new ReturnShipmentRequest("wrong size"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type", is("RETURN")))
            .andExpect(jsonPath("$.parentShipmentId", is(shipmentId.intValue())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long returnShipmentId = objectMapper.readTree(returnBody).get("id").asLong();

    shipmentProgressJob.advancePendingShipments();
    shipmentProgressJob.advancePendingShipments();
    shipmentProgressJob.advancePendingShipments();

    mockMvc
        .perform(
            get("/shipments/" + returnShipmentId)
                .header("Authorization", "Bearer " + tokenFor("alice", "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("DELIVERED")));

    mockMvc
        .perform(
            get("/shipments/order/900")
                .header("Authorization", "Bearer " + tokenFor("alice", "CUSTOMER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));

    String mallory = "Bearer " + tokenFor("mallory", "CUSTOMER");
    mockMvc
        .perform(get("/shipments/" + shipmentId).header("Authorization", mallory))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/shipments/" + shipmentId + "/tracking").header("Authorization", mallory))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/shipments/order/900").header("Authorization", mallory))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
    mockMvc
        .perform(
            post("/shipments/" + shipmentId + "/return")
                .header("Authorization", mallory)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ReturnShipmentRequest("not mine"))))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get("/shipments/" + shipmentId)
                .header("Authorization", "Bearer " + tokenFor("admin", "ADMIN")))
        .andExpect(status().isOk());
    // The orders saga's service identity reads any shipment (e.g. the delivered-check on cancel).
    mockMvc
        .perform(
            get("/shipments/" + shipmentId)
                .header("Authorization", "Bearer " + tokenFor("orders-service", "SERVICE")))
        .andExpect(status().isOk());
  }

  @Test
  void serviceIdentityCanCreateShipments() throws Exception {
    mockMvc
        .perform(
            post("/shipments")
                .header("Authorization", "Bearer " + tokenFor("orders-service", "SERVICE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateShipmentRequest(901L, "alice", "1 Test Way"))))
        .andExpect(status().isCreated());
  }
}
