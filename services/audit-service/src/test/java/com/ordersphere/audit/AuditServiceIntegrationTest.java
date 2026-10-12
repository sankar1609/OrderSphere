package com.ordersphere.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ordersphere.audit.chain.AuditAnchorService;
import com.ordersphere.audit.chain.AuditVerifier;
import com.ordersphere.audit.chain.VerificationResult;
import com.ordersphere.audit.domain.AuditEntry;
import com.ordersphere.audit.domain.ChainHead;
import com.ordersphere.audit.repository.AuditEntryRepository;
import com.ordersphere.events.OrderConfirmedEvent;
import com.ordersphere.events.OrderCreatedEvent;
import com.ordersphere.events.UserRegisteredEvent;
import com.ordersphere.events.messaging.EventsAutoConfiguration;
import com.ordersphere.security.testing.TestJwtIssuer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The audit log against real PostgreSQL and RabbitMQ: events published through the shared relay (as
 * every service does) arrive as chained entries; the database refuses edits; and when someone edits
 * it anyway (triggers disabled, as a database administrator could), verification says where.
 */
@Testcontainers
@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      // The scheduled runs would race the tests' own verify/anchor calls.
      "audit.verify.interval=PT24H",
      "audit.anchor.interval=PT24H"
    })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class AuditServiceIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationEventPublisher events;
  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private AuditEntryRepository entries;
  @Autowired private AuditVerifier verifier;
  @Autowired private AuditAnchorService anchors;
  @Autowired private JdbcTemplate jdbc;

  private static final String AUDITOR = TestJwtIssuer.bearer("audrey", "AUDITOR");

  /** An empty chain for every test. Only possible with the append-only triggers switched off. */
  @BeforeEach
  void emptyChain() {
    withTriggersDisabled(
        () -> {
          jdbc.update("DELETE FROM audit_entries");
          jdbc.update("DELETE FROM audit_anchors");
          jdbc.update("UPDATE audit_chain_head SET seq = 0, hash = ?", ChainHead.GENESIS_HASH);
        });
  }

  private void withTriggersDisabled(Runnable change) {
    jdbc.execute("ALTER TABLE audit_entries DISABLE TRIGGER USER");
    jdbc.execute("ALTER TABLE audit_anchors DISABLE TRIGGER USER");
    try {
      change.run();
    } finally {
      jdbc.execute("ALTER TABLE audit_entries ENABLE TRIGGER USER");
      jdbc.execute("ALTER TABLE audit_anchors ENABLE TRIGGER USER");
    }
  }

  /** Publishes the way a service does while handling alice's request, and waits for the entries. */
  private void recordAnOrder(long orderId) {
    long before = entries.count();
    try (var user = MDC.putCloseable("user", "alice")) {
      events.publishEvent(new OrderCreatedEvent(orderId, "alice", Map.of("SKU-1", 2)));
      events.publishEvent(new OrderConfirmedEvent(orderId, "alice"));
    }
    await().atMost(Duration.ofSeconds(15)).until(() -> entries.count() == before + 2);
  }

  @Test
  void recordsPublishedEventsAsAChainInOrderWithWhoAndWhere() {
    recordAnOrder(42);

    List<AuditEntry> chain = entries.findByOrderIdOrderBySeqAsc(42L);
    assertThat(chain).extracting(AuditEntry::getSeq).containsExactly(1L, 2L);
    assertThat(chain)
        .extracting(AuditEntry::getEventType)
        .containsExactly("OrderCreatedEvent", "OrderConfirmedEvent");
    AuditEntry first = chain.get(0);
    AuditEntry second = chain.get(1);
    assertThat(first.getPrevHash()).isEqualTo(ChainHead.GENESIS_HASH);
    assertThat(second.getPrevHash()).isEqualTo(first.getHash());
    assertThat(first.getActor()).isEqualTo("alice");
    assertThat(first.getSourceService()).isEqualTo("audit-service");
    assertThat(first.getSubjectUsername()).isEqualTo("alice");
    assertThat(first.getRoutingKey()).isEqualTo("order.created");
    assertThat(first.getPayload()).contains("\"orderId\":42").contains("SKU-1");

    assertThat(verifier.verify().valid()).isTrue();
  }

  @Test
  void recordsARedeliveredEventOnlyOnce() {
    OrderConfirmedEvent event = new OrderConfirmedEvent(7L, "bob");

    rabbitTemplate.convertAndSend(
        EventsAutoConfiguration.EVENTS_EXCHANGE, "order.confirmed", event);
    rabbitTemplate.convertAndSend(
        EventsAutoConfiguration.EVENTS_EXCHANGE, "order.confirmed", event);
    events.publishEvent(new UserRegisteredEvent(9L, "carol", "CUSTOMER"));

    await().atMost(Duration.ofSeconds(15)).until(() -> entries.count() == 2);
    assertThat(entries.findAll())
        .extracting(AuditEntry::getEventType)
        .containsExactlyInAnyOrder("OrderConfirmedEvent", "UserRegisteredEvent");
    assertThat(verifier.verify().valid()).isTrue();
  }

  @Test
  void theDatabaseRefusesToChangeOrRemoveAnEntry() {
    recordAnOrder(42);

    assertThatThrownBy(
            () -> jdbc.update("UPDATE audit_entries SET actor = 'mallory' WHERE seq = 1"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_entries WHERE seq = 1"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_entries"))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("append-only");
  }

  @Test
  void findsAnEntryWhoseContentWasAltered() {
    recordAnOrder(42);
    recordAnOrder(43);

    withTriggersDisabled(
        () ->
            jdbc.update(
                "UPDATE audit_entries SET payload = replace(payload, 'alice', 'mallory') WHERE seq = 2"));

    VerificationResult result = verifier.verify();
    assertThat(result.valid()).isFalse();
    assertThat(result.firstBrokenSeq()).isEqualTo(2L);
    assertThat(result.reason()).contains("altered");
    assertThat(result.entriesChecked()).isEqualTo(1);
  }

  @Test
  void findsAnAlteredActorEvenThoughThePayloadIsUntouched() {
    recordAnOrder(42);

    withTriggersDisabled(
        () -> jdbc.update("UPDATE audit_entries SET actor = 'mallory' WHERE seq = 1"));

    assertThat(verifier.verify().firstBrokenSeq()).isEqualTo(1L);
  }

  @Test
  void findsARemovedEntry() {
    recordAnOrder(42);
    recordAnOrder(43);

    withTriggersDisabled(() -> jdbc.update("DELETE FROM audit_entries WHERE seq = 3"));

    VerificationResult result = verifier.verify();
    assertThat(result.valid()).isFalse();
    assertThat(result.firstBrokenSeq()).isEqualTo(3L);
    assertThat(result.reason()).contains("missing");
  }

  @Test
  void findsARemovedTailBecauseTheHeadAndTheAnchorRememberIt() {
    recordAnOrder(42);
    recordAnOrder(43);
    anchors.anchorHead();

    withTriggersDisabled(() -> jdbc.update("DELETE FROM audit_entries WHERE seq >= 3"));
    assertThat(verifier.verify().valid()).as("head still records entry 4").isFalse();

    // Even with the head rewritten to match the shortened chain, the anchor gives it away.
    jdbc.update(
        "UPDATE audit_chain_head SET seq = 2, hash = (SELECT hash FROM audit_entries WHERE seq = 2)");
    VerificationResult result = verifier.verify();
    assertThat(result.valid()).isFalse();
    assertThat(result.firstBrokenSeq()).isEqualTo(4L);
    assertThat(result.reason()).contains("anchored");
  }

  @Test
  void anchorsTheHeadOnlyWhenItHasMoved() {
    assertThat(anchors.anchorHead()).as("empty chain").isEmpty();

    recordAnOrder(42);
    assertThat(anchors.anchorHead())
        .hasValueSatisfying(
            anchor -> {
              assertThat(anchor.getSeq()).isEqualTo(2);
              assertThat(anchor.getTarget()).isEqualTo("log");
              assertThat(anchor.getHash()).isEqualTo(entries.findById(2L).orElseThrow().getHash());
            });
    assertThat(anchors.anchorHead()).as("nothing new").isEmpty();
    assertThat(verifier.verify().valid()).isTrue();
  }

  @Test
  void auditorsAndAdminsCanReadEveryoneElseCannot() throws Exception {
    recordAnOrder(42);

    mockMvc.perform(get("/audit/entries")).andExpect(status().isUnauthorized());
    for (String role : List.of("CUSTOMER", "VENDOR", "SERVICE")) {
      mockMvc
          .perform(get("/audit/entries").header("Authorization", TestJwtIssuer.bearer("u", role)))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(post("/audit/verify").header("Authorization", TestJwtIssuer.bearer("u", role)))
          .andExpect(status().isForbidden());
    }
    for (String role : List.of("AUDITOR", "ADMIN")) {
      mockMvc
          .perform(get("/audit/entries").header("Authorization", TestJwtIssuer.bearer("u", role)))
          .andExpect(status().isOk());
    }
  }

  @Test
  void searchesNewestFirstAndKeepsPayloadsOutOfLists() throws Exception {
    recordAnOrder(42);
    recordAnOrder(43);

    mockMvc
        .perform(get("/audit/entries").header("Authorization", AUDITOR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total", is(4)))
        .andExpect(jsonPath("$.items[*].seq", contains(4, 3, 2, 1)))
        .andExpect(jsonPath("$.items[0].payload", nullValue()))
        .andExpect(jsonPath("$.items[0].hash", nullValue()));
    mockMvc
        .perform(
            get("/audit/entries")
                .param("orderId", "43")
                .param("eventType", "OrderConfirmedEvent")
                .param("actor", "alice")
                .header("Authorization", AUDITOR))
        .andExpect(jsonPath("$.items", hasSize(1)))
        .andExpect(jsonPath("$.items[0].seq", is(4)));
    mockMvc
        .perform(get("/audit/entries").param("actor", "nobody").header("Authorization", AUDITOR))
        .andExpect(jsonPath("$.total", is(0)));
    mockMvc
        .perform(get("/audit/entries").param("size", "500").header("Authorization", AUDITOR))
        .andExpect(status().isBadRequest());
  }

  @Test
  void showsAnOrdersTimelineAndAnEntrysDetail() throws Exception {
    recordAnOrder(42);
    recordAnOrder(43);

    mockMvc
        .perform(get("/audit/orders/42").header("Authorization", AUDITOR))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].eventType", contains("OrderCreatedEvent", "OrderConfirmedEvent")));
    mockMvc
        .perform(get("/audit/entries/1").header("Authorization", AUDITOR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.actor", is("alice")))
        .andExpect(jsonPath("$.prevHash", is(ChainHead.GENESIS_HASH)))
        .andExpect(jsonPath("$.payload").isNotEmpty());
    mockMvc
        .perform(get("/audit/entries/999").header("Authorization", AUDITOR))
        .andExpect(status().isNotFound());
  }

  @Test
  void verifiesOnDemandAndReportsStatus() throws Exception {
    recordAnOrder(42);

    mockMvc
        .perform(post("/audit/verify").header("Authorization", AUDITOR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid", is(true)))
        .andExpect(jsonPath("$.entriesChecked", is(2)))
        .andExpect(jsonPath("$.headSeq", is(2)));
    mockMvc
        .perform(get("/audit/status").header("Authorization", AUDITOR))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries", is(2)))
        .andExpect(jsonPath("$.lastVerification.valid", is(true)))
        .andExpect(jsonPath("$.lastAnchor", nullValue()));

    withTriggersDisabled(
        () -> jdbc.update("UPDATE audit_entries SET event_type = 'X' WHERE seq = 1"));
    mockMvc
        .perform(post("/audit/verify").header("Authorization", AUDITOR))
        .andExpect(jsonPath("$.valid", is(false)))
        .andExpect(jsonPath("$.firstBrokenSeq", is(1)));
  }
}
