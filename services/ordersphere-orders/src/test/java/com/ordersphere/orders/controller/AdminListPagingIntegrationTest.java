package com.ordersphere.orders.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ordersphere.orders.domain.Compensation;
import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderStatus;
import com.ordersphere.orders.repository.CompensationRepository;
import com.ordersphere.orders.repository.OrderRepository;
import com.ordersphere.security.testing.TestJwtIssuer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The admin lists of compensations and unshipped orders, a page at a time. */
@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class AdminListPagingIntegrationTest {

  private static final Instant SAME_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private CompensationRepository compensationRepository;
  @Autowired private OrderRepository orderRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void startEmpty() {
    compensationRepository.deleteAll();
    orderRepository.deleteAll();
  }

  private MockHttpServletRequestBuilder asAdmin(String path) {
    return get(path).header("Authorization", TestJwtIssuer.bearer("root", "ADMIN"));
  }

  /** Saves {@code count} rows in a status, all updated at the same instant; returns their ids. */
  private List<Long> compensations(Compensation.Status status, int count) {
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Compensation row =
          new Compensation(Compensation.Type.REFUND_PAYMENT, 1L + i, 2L, "test", SAME_INSTANT);
      row.setStatus(status);
      ids.add(compensationRepository.save(row).getId());
    }
    return ids;
  }

  /** Saves {@code count} paid orders without a shipment, updated at the same instant. */
  private List<Long> unshippedOrders(int count) {
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Order order = new Order("customer");
      order.setStatus(OrderStatus.CONFIRMED);
      ids.add(orderRepository.save(order).getId());
    }
    jdbcTemplate.update("update orders set updated_at = ?", java.sql.Timestamp.from(SAME_INSTANT));
    return ids;
  }

  /** Every id seen reading the list one row per page. */
  private List<Integer> readPageByPage(String path, String idField, int rows) throws Exception {
    List<Integer> seen = new ArrayList<>();
    for (int page = 0; page < rows; page++) {
      String body =
          mockMvc
              .perform(asAdmin(path).param("size", "1").param("page", String.valueOf(page)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      seen.add(JsonPath.read(body, "$.items[0]." + idField));
    }
    return seen;
  }

  private static Integer[] newestFirst(List<Long> ids) {
    List<Long> reversed = new ArrayList<>(ids);
    Collections.reverse(reversed);
    return reversed.stream().map(Long::intValue).toArray(Integer[]::new);
  }

  @Test
  void compensationsArePagedWithinTheirStatus() throws Exception {
    List<Long> failed = compensations(Compensation.Status.FAILED, 3);
    compensations(Compensation.Status.DONE, 2);

    mockMvc
        .perform(asAdmin("/orders/admin/compensations").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total", is(3)))
        .andExpect(jsonPath("$.size", is(2)))
        .andExpect(
            jsonPath(
                "$.items[*].id", contains(failed.get(2).intValue(), failed.get(1).intValue())));
    mockMvc
        .perform(asAdmin("/orders/admin/compensations").param("status", "DONE"))
        .andExpect(jsonPath("$.total", is(2)));
  }

  @Test
  void compensationsUpdatedAtTheSameInstantAreEachOnExactlyOnePage() throws Exception {
    List<Long> ids = compensations(Compensation.Status.FAILED, 6);

    Assertions.assertThat(readPageByPage("/orders/admin/compensations", "id", 6))
        .containsExactly(newestFirst(ids));
  }

  @Test
  void unshippedOrdersArePagedOldestFirstAndLeaveOutShippedOnes() throws Exception {
    List<Long> ids = unshippedOrders(3);
    Order shipped = new Order("customer");
    shipped.setStatus(OrderStatus.CONFIRMED);
    shipped.setShipmentId(9L);
    orderRepository.save(shipped);
    Order cancelled = new Order("customer");
    cancelled.setStatus(OrderStatus.CANCELLED);
    orderRepository.save(cancelled);

    mockMvc
        .perform(asAdmin("/orders/admin/unshipped").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total", is(3)))
        .andExpect(
            jsonPath("$.items[*].orderId", contains(ids.get(0).intValue(), ids.get(1).intValue())));
    mockMvc
        .perform(asAdmin("/orders/admin/unshipped").param("size", "2").param("page", "1"))
        .andExpect(jsonPath("$.items", hasSize(1)));
  }

  @Test
  void unshippedOrdersUpdatedAtTheSameInstantAreEachOnExactlyOnePage() throws Exception {
    List<Long> ids = unshippedOrders(6);

    Assertions.assertThat(readPageByPage("/orders/admin/unshipped", "orderId", 6))
        .containsExactly(ids.stream().map(Long::intValue).toArray(Integer[]::new));
  }

  @Test
  void outOfRangeOrMalformedParametersAre400() throws Exception {
    for (String path : List.of("/orders/admin/compensations", "/orders/admin/unshipped")) {
      for (String[] param :
          new String[][] {
            {"size", "0"}, {"size", "201"}, {"page", "-1"}, {"page", "2147483647"}, {"size", "lots"}
          }) {
        mockMvc.perform(asAdmin(path).param(param[0], param[1])).andExpect(status().isBadRequest());
      }
    }
  }
}
