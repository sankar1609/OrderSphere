package com.ordersphere.payment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ordersphere.payment.gateway.PaymentGatewayClient;
import com.ordersphere.security.testing.TestJwtIssuer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** GET /payments/admin/reconciliation/findings: a page at a time, in a stable order. */
@Testcontainers
@SpringBootTest(properties = "eureka.client.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class FindingListPagingIntegrationTest {

  private static final String PATH = "/payments/admin/reconciliation/findings";

  /** A run stamps every finding it sees with its own time, so ties are the normal case. */
  private static final Instant SAME_RUN = Instant.parse("2026-01-01T00:00:00Z");

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ReconciliationFindingRepository findings;
  @Autowired private ReconciliationRunRepository runs;
  @MockBean private PaymentGatewayClient gatewayClient;

  private Long runId;

  @BeforeEach
  void startEmpty() {
    findings.deleteAll();
    runId = runs.save(new ReconciliationRun(SAME_RUN, SAME_RUN, SAME_RUN)).getId();
  }

  private List<Long> saveFindings(ReconciliationFinding.Status status, String prefix, int count) {
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ReconciliationFinding finding = new ReconciliationFinding();
      finding.setRunId(runId);
      finding.setType(ReconciliationFinding.Type.UNKNOWN_SESSION);
      finding.setStatus(status);
      finding.setCheckoutSessionId(prefix + i);
      finding.setFirstSeenAt(SAME_RUN);
      finding.setLastSeenAt(SAME_RUN);
      ids.add(findings.save(finding).getId());
    }
    return ids;
  }

  private MockHttpServletRequestBuilder asAdmin() {
    return get(PATH).header("Authorization", TestJwtIssuer.bearer("root", "ADMIN"));
  }

  private static Integer[] newestFirst(List<Long> ids) {
    List<Long> reversed = new ArrayList<>(ids);
    Collections.reverse(reversed);
    return reversed.stream().map(Long::intValue).toArray(Integer[]::new);
  }

  @Test
  void findingsArePagedWithinTheirStatus() throws Exception {
    saveFindings(ReconciliationFinding.Status.OPEN, "cs_open_", 3);
    saveFindings(ReconciliationFinding.Status.RESOLVED, "cs_resolved_", 1);

    mockMvc
        .perform(asAdmin().param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page", is(0)))
        .andExpect(jsonPath("$.size", is(2)))
        .andExpect(jsonPath("$.total", is(3)))
        .andExpect(jsonPath("$.items.length()", is(2)));
    mockMvc
        .perform(asAdmin().param("status", "RESOLVED"))
        .andExpect(jsonPath("$.total", is(1)))
        .andExpect(jsonPath("$.items[0].checkoutSessionId", is("cs_resolved_0")));
  }

  @Test
  void findingsOfTheSameRunAreEachOnExactlyOnePage() throws Exception {
    List<Long> ids = saveFindings(ReconciliationFinding.Status.OPEN, "cs_tied_", 6);

    List<Integer> seen = new ArrayList<>();
    for (int page = 0; page < 6; page++) {
      String body =
          mockMvc
              .perform(asAdmin().param("size", "1").param("page", String.valueOf(page)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      seen.add(JsonPath.read(body, "$.items[0].id"));
    }

    assertThat(seen).containsExactly(newestFirst(ids));
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
          {"status", "BOGUS"}
        }) {
      mockMvc.perform(asAdmin().param(param[0], param[1])).andExpect(status().isBadRequest());
    }
  }

  @Test
  void onlyAdminsCanList() throws Exception {
    mockMvc
        .perform(get(PATH).header("Authorization", TestJwtIssuer.bearer("someone", "CUSTOMER")))
        .andExpect(status().isForbidden());
  }
}
