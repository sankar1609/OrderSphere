package com.ordersphere.orders.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderItem;
import com.ordersphere.orders.repository.OrderRepository;
import com.ordersphere.security.testing.TestJwtIssuer;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** GET /orders: the caller's orders a page at a time, newest first. */
@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class OrderListPagingIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private OrderRepository orderRepository;
  @Autowired private EntityManagerFactory entityManagerFactory;

  /** Saves {@code count} orders of two items each for the customer; returns their ids in order. */
  private List<Long> ordersFor(String customer, int count) {
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Order order = new Order(customer);
      order.addItem(new OrderItem("SKU-A", 1));
      order.addItem(new OrderItem("SKU-B", 2));
      ids.add(orderRepository.save(order).getId());
    }
    return ids;
  }

  private MockHttpServletRequestBuilder listAs(String customer) {
    return get("/orders").header("Authorization", TestJwtIssuer.bearer(customer, "CUSTOMER"));
  }

  @Test
  void pagesAreNewestFirstAndCountTheWholeList() throws Exception {
    List<Long> ids = ordersFor("pager", 5);

    mockMvc
        .perform(listAs("pager").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page", is(0)))
        .andExpect(jsonPath("$.size", is(2)))
        .andExpect(jsonPath("$.total", is(5)))
        .andExpect(
            jsonPath("$.items[*].id", contains(ids.get(4).intValue(), ids.get(3).intValue())))
        .andExpect(jsonPath("$.items[0].items", hasSize(2)));
    mockMvc
        .perform(listAs("pager").param("size", "2").param("page", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].id", contains(ids.get(0).intValue())));
    // Past the end: an empty page, not an error.
    mockMvc
        .perform(listAs("pager").param("size", "2").param("page", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.total", is(5)));
  }

  @Test
  void withoutParametersTheFirstTwentyAreReturned() throws Exception {
    ordersFor("many", 25);

    mockMvc
        .perform(listAs("many"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size", is(20)))
        .andExpect(jsonPath("$.items", hasSize(20)))
        .andExpect(jsonPath("$.total", is(25)));
  }

  @Test
  void aCustomerOnlyPagesThroughTheirOwnOrders() throws Exception {
    ordersFor("owner-a", 3);
    ordersFor("owner-b", 4);

    mockMvc
        .perform(listAs("owner-a"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total", is(3)))
        .andExpect(
            jsonPath("$.items[*].customerUsername", contains("owner-a", "owner-a", "owner-a")));
  }

  @Test
  void outOfRangeOrMalformedParametersAre400() throws Exception {
    for (String[] param :
        new String[][] {
          {"size", "0"}, {"size", "201"}, {"page", "-1"}, {"page", "2147483647"}, {"size", "lots"}
        }) {
      mockMvc
          .perform(listAs("anyone").param(param[0], param[1]))
          .andExpect(status().isBadRequest());
    }
    mockMvc.perform(listAs("anyone").param("size", "200")).andExpect(status().isOk());
  }

  @Test
  void aPageLoadsItsOrdersItemsInOneQuery() throws Exception {
    ordersFor("counted", 20);
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();

    mockMvc.perform(listAs("counted")).andExpect(status().isOk());

    // The page, its count and all twenty orders' items - not one query per order.
    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    statistics.setStatisticsEnabled(false);
  }
}
