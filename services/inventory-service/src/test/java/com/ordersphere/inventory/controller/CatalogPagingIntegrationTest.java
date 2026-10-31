package com.ordersphere.inventory.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ordersphere.security.testing.TestJwtIssuer;
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

/** GET /inventory/products: the catalog a page at a time in SKU order, with an optional search. */
@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class CatalogPagingIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  private static final String ADMIN = TestJwtIssuer.bearer("test-admin", "ADMIN");
  private static final String CUSTOMER = TestJwtIssuer.bearer("test-customer", "CUSTOMER");

  @Autowired private MockMvc mockMvc;

  private void product(String sku, String name) throws Exception {
    mockMvc
        .perform(
            post("/inventory/products")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"sku\":\""
                        + sku
                        + "\",\"name\":\""
                        + name
                        + "\",\"quantityOnHand\":5,\"reorderThreshold\":0,\"unitPrice\":2.00}"))
        .andExpect(status().isCreated());
  }

  private MockHttpServletRequestBuilder catalog() {
    return get("/inventory/products").header("Authorization", CUSTOMER);
  }

  @Test
  void pagesAreInSkuOrderAndCountTheWholeCatalog() throws Exception {
    product("PG-C", "Gamma");
    product("PG-A", "Alpha");
    product("PG-B", "Beta");

    mockMvc
        .perform(catalog().param("q", "pg-").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page", is(0)))
        .andExpect(jsonPath("$.size", is(2)))
        .andExpect(jsonPath("$.total", is(3)))
        .andExpect(jsonPath("$.items[*].sku", contains("PG-A", "PG-B")));
    mockMvc
        .perform(catalog().param("q", "pg-").param("size", "2").param("page", "1"))
        .andExpect(jsonPath("$.items[*].sku", contains("PG-C")));
  }

  @Test
  void withoutASearchTheFirstFiftyAreReturned() throws Exception {
    for (int i = 0; i < 52; i++) {
      product(String.format("BULK-%02d", i), "Bulk item");
    }

    mockMvc
        .perform(catalog())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size", is(50)))
        .andExpect(jsonPath("$.items", hasSize(50)));
  }

  @Test
  void theSearchMatchesSkuOrNameIgnoringCase() throws Exception {
    product("SRCH-1", "Blue Teapot");
    product("SRCH-2", "Red Kettle");
    product("TEAPOT-9", "Plain pot");

    mockMvc
        .perform(catalog().param("q", "TEAPOT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total", is(2)))
        .andExpect(jsonPath("$.items[*].sku", contains("SRCH-1", "TEAPOT-9")));
    // Surrounding spaces are ignored.
    mockMvc
        .perform(catalog().param("q", "  kettle "))
        .andExpect(jsonPath("$.items[*].sku", contains("SRCH-2")));
  }

  @Test
  void wildcardCharactersInTheSearchAreLiteral() throws Exception {
    product("LIT_1", "Underscore");
    product("LITX1", "No underscore");

    // In SQL LIKE "_" is any one character; here it is just an underscore.
    mockMvc
        .perform(catalog().param("q", "lit_"))
        .andExpect(jsonPath("$.items[*].sku", contains("LIT_1")));
    mockMvc.perform(catalog().param("q", "%")).andExpect(jsonPath("$.total", is(0)));
  }

  @Test
  void outOfRangeOrMalformedParametersAre400() throws Exception {
    mockMvc.perform(catalog().param("size", "0")).andExpect(status().isBadRequest());
    mockMvc.perform(catalog().param("size", "201")).andExpect(status().isBadRequest());
    mockMvc.perform(catalog().param("page", "-1")).andExpect(status().isBadRequest());
    mockMvc.perform(catalog().param("page", "2147483647")).andExpect(status().isBadRequest());
    mockMvc.perform(catalog().param("page", "first")).andExpect(status().isBadRequest());
    mockMvc.perform(catalog().param("q", "x".repeat(256))).andExpect(status().isBadRequest());
  }
}
