package com.ordersphere.auth.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.auth.domain.Role;
import com.ordersphere.auth.domain.User;
import com.ordersphere.auth.dto.LoginRequest;
import com.ordersphere.auth.dto.RegisterRequest;
import com.ordersphere.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** GET /auth/admin/users: users a page at a time, with a search on the server. */
@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class AdminUserPagingIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;

  private void users(String... usernames) {
    for (String username : usernames) {
      userRepository.save(new User(username, "hashed", Role.CUSTOMER));
    }
  }

  private String loginToken(String username, String password) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("token").asText();
  }

  private MockHttpServletRequestBuilder listAsAdmin() throws Exception {
    return get("/auth/admin/users")
        .header("Authorization", "Bearer " + loginToken("admin", "admin123"));
  }

  @Test
  void pagesAreOldestFirstAndCountTheWholeList() throws Exception {
    users("paged.one", "paged.two", "paged.three");

    mockMvc
        .perform(listAsAdmin().param("q", "paged.").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page", is(0)))
        .andExpect(jsonPath("$.size", is(2)))
        .andExpect(jsonPath("$.total", is(3)))
        .andExpect(jsonPath("$.items[*].username", contains("paged.one", "paged.two")));
    mockMvc
        .perform(listAsAdmin().param("q", "paged.").param("size", "2").param("page", "1"))
        .andExpect(jsonPath("$.items[*].username", contains("paged.three")));
  }

  @Test
  void theSearchIgnoresCaseFindsAnyPartOfTheNameAndCountsOnlyMatches() throws Exception {
    users("Zelda.Search", "other-user");

    mockMvc
        .perform(listAsAdmin().param("q", "  LDA.se "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total", is(1)))
        .andExpect(jsonPath("$.items[0].username", is("Zelda.Search")));
  }

  @Test
  void wildcardsInTheSearchAreLiteral() throws Exception {
    users("under_score", "underXscore", "100pct");

    mockMvc
        .perform(listAsAdmin().param("q", "under_"))
        .andExpect(jsonPath("$.items[*].username", contains("under_score")));
    mockMvc
        .perform(listAsAdmin().param("q", "%"))
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.total", is(0)));
  }

  @Test
  void aBlankSearchListsEveryone() throws Exception {
    users("blank-one");
    long everyone = userRepository.count();

    mockMvc
        .perform(listAsAdmin().param("q", "   "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total", is((int) everyone)));
  }

  @Test
  void outOfRangeOrMalformedParametersAre400() throws Exception {
    for (String[] param :
        new String[][] {
          {"size", "0"},
          {"size", "201"},
          {"page", "-1"},
          {"page", "2147483647"},
          {"size", "lots"},
          {"q", "x".repeat(51)}
        }) {
      mockMvc.perform(listAsAdmin().param(param[0], param[1])).andExpect(status().isBadRequest());
    }
  }

  @Test
  void onlyAdminsCanList() throws Exception {
    mockMvc
        .perform(
            post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterRequest("someone", "password123", Role.CUSTOMER))))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/auth/admin/users")
                .header("Authorization", "Bearer " + loginToken("someone", "password123")))
        .andExpect(status().isForbidden());
  }
}
