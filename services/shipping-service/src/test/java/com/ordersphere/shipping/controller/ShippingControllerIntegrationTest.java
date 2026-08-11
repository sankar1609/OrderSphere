package com.ordersphere.shipping.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.security.JwtTokenProvider;
import com.ordersphere.shipping.dto.CreateShipmentRequest;
import com.ordersphere.shipping.dto.ReturnShipmentRequest;
import com.ordersphere.shipping.service.ShipmentProgressJob;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
@AutoConfigureMockMvc
class ShippingControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtTokenProvider jwtTokenProvider;
  @Autowired private ShipmentProgressJob shipmentProgressJob;

  private String tokenFor(String username, String role) {
    return jwtTokenProvider.generateToken(username, Map.of("role", role));
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
                    objectMapper.writeValueAsString(new CreateShipmentRequest(900L, "1 Test Way"))))
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
                            new CreateShipmentRequest(900L, "1 Test Way"))))
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
  }
}
