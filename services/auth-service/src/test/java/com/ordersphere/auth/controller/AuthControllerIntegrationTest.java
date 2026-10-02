package com.ordersphere.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.auth.domain.Role;
import com.ordersphere.auth.dto.LoginRequest;
import com.ordersphere.auth.dto.RefreshRequest;
import com.ordersphere.auth.dto.RegisterRequest;
import com.ordersphere.auth.dto.RoleChangeRequest;
import com.ordersphere.security.JwtVerifier;
import com.ordersphere.security.RsaKeys;
import io.jsonwebtoken.Claims;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
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
// Grace window off: these tests reuse a token seconds after rotating it and expect reuse
// detection. The concurrent-refresh grace itself is covered by RefreshTokenServiceTest.
@SpringBootTest(properties = {"eureka.client.enabled=false", "auth.refresh-token-reuse-grace=PT0S"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JwtVerifier jwtVerifier;

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

  @Test
  void usernamesMustBeWellFormedAndUniqueRegardlessOfCase() throws Exception {
    register("lena");
    for (String bad : List.of("LENA", " lena", "le na", "x".repeat(51), "ab")) {
      mockMvc
          .perform(
              post("/auth/register")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new RegisterRequest(bad, "password123", Role.CUSTOMER))))
          .andExpect(status().is4xxClientError());
    }
    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterRequest("longpw", "p".repeat(73), Role.CUSTOMER))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void adminsCanListUsersButNotChangeTheirOwnRole() throws Exception {
    register("kate");
    String adminToken = loginAndGetToken("admin", "admin123");

    String users =
        mockMvc
            .perform(get("/auth/admin/users").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.username == 'kate')].role").value("CUSTOMER"))
            .andExpect(jsonPath("$[?(@.username == 'kate')].createdAt").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long adminId = 0;
    for (JsonNode user : objectMapper.readTree(users)) {
      if (user.get("username").asText().equals("admin")) {
        adminId = user.get("id").asLong();
      }
    }

    mockMvc
        .perform(
            get("/auth/admin/users")
                .header("Authorization", "Bearer " + loginAndGetToken("kate", "password123")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/auth/admin/users/" + adminId + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RoleChangeRequest(Role.CUSTOMER))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", is("You can't change your own role")));
  }

  @Test
  void loginTokensVerifyAgainstThePublishedJwks() throws Exception {
    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterRequest("gina", "password123", Role.CUSTOMER))))
        .andExpect(status().isCreated());
    String token = loginAndGetToken("gina", "password123");

    JsonNode jwks =
        objectMapper.readTree(
            mockMvc
                .perform(get("/auth/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    JsonNode key = jwks.get("keys").get(0);
    RSAPublicKey publicKey = RsaKeys.fromJwk(key.get("n").asText(), key.get("e").asText());
    String kid = key.get("kid").asText();
    JwtVerifier verifier =
        new JwtVerifier(k -> kid.equals(k) ? publicKey : null, "ordersphere-auth");

    Claims claims = verifier.verify(token);
    assertThat(claims.getSubject()).isEqualTo("gina");
    assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
    assertThat(claims.get("typ", String.class)).isEqualTo("access");
  }

  @Test
  void clientCredentialsIssueAServiceToken() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/auth/token")
                    .header("Authorization", basic("orders-service", "dev-orders-client-secret"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "client_credentials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token_type", is("Bearer")))
            .andExpect(jsonPath("$.expires_in", is(300)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String serviceToken = objectMapper.readTree(body).get("access_token").asText();

    Claims claims = jwtVerifier.verify(serviceToken);
    assertThat(claims.getSubject()).isEqualTo("orders-service");
    assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
    assertThat(claims.get("typ", String.class)).isEqualTo("service");
  }

  @Test
  void clientCredentialsRejectBadSecretsAndGrants() throws Exception {
    mockMvc
        .perform(
            post("/auth/token")
                .header("Authorization", basic("orders-service", "wrong-secret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error", is("invalid_client")));
    mockMvc
        .perform(
            post("/auth/token")
                .header("Authorization", basic("unknown-client", "whatever"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/auth/token")
                .header("Authorization", basic("orders-service", "dev-orders-client-secret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "password"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("unsupported_grant_type")));
  }

  @Test
  void refreshRotatesTokensAndReuseRevokesTheWholeSession() throws Exception {
    register("hank");
    JsonNode login = loginResponse("hank", "password123");
    String first = login.get("refreshToken").asText();

    JsonNode refreshed = refresh(first, 200);
    String second = refreshed.get("refreshToken").asText();
    assertThat(second).isNotEqualTo(first);
    assertThat(jwtVerifier.verify(refreshed.get("token").asText()).getSubject()).isEqualTo("hank");

    // Replaying the already-used token looks like theft: it fails, and so does the token that
    // replaced it, because the whole session (token family) is revoked.
    refresh(first, 401);
    refresh(second, 401);
  }

  @Test
  void logoutEndsOnlyThatSession() throws Exception {
    register("ivy");
    String laptop = loginResponse("ivy", "password123").get("refreshToken").asText();
    String phone = loginResponse("ivy", "password123").get("refreshToken").asText();

    mockMvc
        .perform(
            post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest(laptop))))
        .andExpect(status().isNoContent());

    refresh(laptop, 401);
    refresh(phone, 200);
  }

  @Test
  void logoutEverywhereAndRoleChangesEndAllSessions() throws Exception {
    register("jack");
    JsonNode login = loginResponse("jack", "password123");
    String laptop = login.get("refreshToken").asText();
    String phone = loginResponse("jack", "password123").get("refreshToken").asText();

    mockMvc
        .perform(
            post("/auth/logout-all")
                .header("Authorization", "Bearer " + login.get("token").asText()))
        .andExpect(status().isNoContent());
    refresh(laptop, 401);
    refresh(phone, 401);

    JsonNode relogin = loginResponse("jack", "password123");
    String adminToken = loginAndGetToken("admin", "admin123");
    Long jackId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        get("/auth/me")
                            .header("Authorization", "Bearer " + relogin.get("token").asText()))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    mockMvc
        .perform(
            patch("/auth/admin/users/" + jackId + "/role")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RoleChangeRequest(Role.VENDOR))))
        .andExpect(status().isOk());
    refresh(relogin.get("refreshToken").asText(), 401);
  }

  @Test
  void unknownRefreshTokenIsRejected() throws Exception {
    refresh("not-a-real-refresh-token", 401);
  }

  private void register(String username) throws Exception {
    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterRequest(username, "password123", Role.CUSTOMER))))
        .andExpect(status().isCreated());
  }

  private JsonNode loginResponse(String username, String password) throws Exception {
    return objectMapper.readTree(
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode refresh(String refreshToken, int expectedStatus) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
            .andExpect(status().is(expectedStatus))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return body.isEmpty() ? null : objectMapper.readTree(body);
  }

  private static String basic(String clientId, String secret) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
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

  @Test
  void publishesItsOpenApiSpecWithoutAToken() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.servers[0].url")
                .value("/auth-service"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                "$.paths['/auth/login'].post.security", org.hamcrest.Matchers.empty()))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                "$.paths['/auth/admin/users'].get.description",
                org.hamcrest.Matchers.containsString("Requires role: ADMIN")));
  }
}
