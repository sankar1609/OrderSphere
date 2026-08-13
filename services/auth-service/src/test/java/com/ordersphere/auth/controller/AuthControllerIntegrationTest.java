package com.ordersphere.auth.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.auth.domain.Role;
import com.ordersphere.auth.dto.LoginRequest;
import com.ordersphere.auth.dto.RegisterRequest;
import com.ordersphere.auth.dto.RoleChangeRequest;
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
class AuthControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void customerCanRegisterLoginAndFetchOwnProfile() throws Exception {
    RegisterRequest registerRequest = new RegisterRequest("alice", "password123", Role.CUSTOMER);

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username", is("alice")))
        .andExpect(jsonPath("$.role", is("CUSTOMER")));

    String token = loginAndGetToken("alice", "password123");

    mockMvc
        .perform(get("/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username", is("alice")))
        .andExpect(jsonPath("$.role", is("CUSTOMER")));
  }

  @Test
  void registrationRejectsAdminSelfSelection() throws Exception {
    RegisterRequest registerRequest = new RegisterRequest("bob", "password123", Role.ADMIN);

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void registrationRejectsDuplicateUsername() throws Exception {
    RegisterRequest registerRequest = new RegisterRequest("carol", "password123", Role.VENDOR);
    mockMvc.perform(
        post("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)));

    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
        .andExpect(status().isConflict());
  }

  @Test
  void loginRejectsWrongPassword() throws Exception {
    RegisterRequest registerRequest = new RegisterRequest("dave", "password123", Role.CUSTOMER);
    mockMvc.perform(
        post("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)));

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginRequest("dave", "wrong-password"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void meRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void nonAdminCannotChangeRoles() throws Exception {
    RegisterRequest registerRequest = new RegisterRequest("erin", "password123", Role.CUSTOMER);
    mockMvc.perform(
        post("/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)));
    String token = loginAndGetToken("erin", "password123");

    mockMvc
        .perform(
            patch("/auth/admin/users/1/role")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RoleChangeRequest(Role.ADMIN))))
        .andExpect(status().isForbidden());
  }

  @Test
  void bootstrapAdminCanPromoteAUser() throws Exception {
    RegisterRequest registerRequest = new RegisterRequest("frank", "password123", Role.CUSTOMER);
    String registerResponse =
        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long frankId = objectMapper.readTree(registerResponse).get("id").asLong();

    String adminToken = loginAndGetToken("admin", "admin123");

    mockMvc
        .perform(
            patch("/auth/admin/users/" + frankId + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RoleChangeRequest(Role.VENDOR))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role", is("VENDOR")));
  }

  private String loginAndGetToken(String username, String password) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).get("token").asText();
  }
}
