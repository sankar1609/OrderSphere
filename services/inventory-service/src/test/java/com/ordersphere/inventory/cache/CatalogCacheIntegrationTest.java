package com.ordersphere.inventory.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.repository.ProductRepository;
import com.ordersphere.inventory.service.ReservationExpiryJob;
import com.ordersphere.security.testing.TestJwtIssuer;
import jakarta.persistence.EntityManagerFactory;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The catalog cache with real Redis, PostgreSQL and RabbitMQ: reads stop touching the database,
 * every way a product can change makes the next read fresh, a rolled-back change evicts nothing,
 * and the catalog keeps working while Redis doesn't.
 */
@Testcontainers
@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "inventory.catalog-cache.enabled=true",
      "spring.jpa.properties.hibernate.generate_statistics=true",
      // The scheduled sweep would expire reservations behind the tests' backs.
      "inventory.reservation.expiry-sweep-interval-ms=86400000"
    })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@AutoConfigureMockMvc
class CatalogCacheIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management");

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  private static final AtomicLong IDS = new AtomicLong(System.nanoTime() % 1_000_000);
  private static final String ADMIN = "Bearer " + TestJwtIssuer.token("test-admin", "ADMIN");
  private static final String CUSTOMER =
      "Bearer " + TestJwtIssuer.token("test-customer", "CUSTOMER");
  private static final String SERVICE =
      "Bearer " + TestJwtIssuer.token("orders-service", "SERVICE");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private ProductRepository productRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private ReservationExpiryJob expiryJob;
  @Autowired private JdbcTemplate jdbc;

  private String sku;
  private long orderId;

  @BeforeEach
  void aProductWithTenInStock() throws Exception {
    sku = "CACHE-" + IDS.incrementAndGet();
    orderId = IDS.incrementAndGet();
    mockMvc
        .perform(
            post("/inventory/products")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"sku\":\""
                        + sku
                        + "\",\"name\":\"Cached widget\",\"quantityOnHand\":10,"
                        + "\"reorderThreshold\":0,\"unitPrice\":4.50}"))
        .andExpect(status().isCreated());
  }

  private Statistics statistics() {
    return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  private String listJson() throws Exception {
    return mockMvc
        .perform(get("/inventory/products").header("Authorization", CUSTOMER))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private int availableInList() throws Exception {
    for (JsonNode product : objectMapper.readTree(listJson())) {
      if (sku.equals(product.get("sku").asText())) {
        return product.get("availableQuantity").asInt();
      }
    }
    throw new AssertionError(sku + " is not in the catalog");
  }

  private int availableBySku() throws Exception {
    return objectMapper
        .readTree(
            mockMvc
                .perform(get("/inventory/products/" + sku).header("Authorization", CUSTOMER))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("availableQuantity")
        .asInt();
  }

  /** Both read endpoints, so both cache entries are warm before the change under test. */
  private void warmBoth(int expected) throws Exception {
    assertThat(availableInList()).isEqualTo(expected);
    assertThat(availableBySku()).isEqualTo(expected);
  }

  private void reserve(int quantity) throws Exception {
    mockMvc
        .perform(
            post("/inventory/reservations")
                .header("Authorization", SERVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"orderId\":"
                        + orderId
                        + ",\"items\":[{\"sku\":\""
                        + sku
                        + "\",\"quantity\":"
                        + quantity
                        + "}]}"))
        .andExpect(status().isCreated());
  }

  @Test
  void aRepeatedReadComesFromRedisNotTheDatabase() throws Exception {
    String first = listJson();
    long statementsAfterFirst = statistics().getPrepareStatementCount();

    String second = listJson();
    String third = listJson();
    assertThat(availableBySku()).isEqualTo(10);
    long afterWarmSingle = statistics().getPrepareStatementCount();
    assertThat(availableBySku()).isEqualTo(10);

    assertThat(second).isEqualTo(first).isEqualTo(third);
    assertThat(afterWarmSingle - statementsAfterFirst)
        .as("only the first single-product read queried")
        .isEqualTo(1);
    assertThat(statistics().getPrepareStatementCount()).isEqualTo(afterWarmSingle);
    // Redis holds only the version token - the catalog itself never goes there.
    assertThat(redisTemplate.keys("inventory:*")).containsExactly(CatalogCache.KEY_VERSION);
  }

  @Test
  void aNewProductAppearsInTheListStraightAway() throws Exception {
    // @BeforeEach just created one; the list read before that (by an earlier test) is gone.
    assertThat(availableInList()).isEqualTo(10);
  }

  @Test
  void restockingShowsAtOnce() throws Exception {
    warmBoth(10);

    mockMvc
        .perform(
            post("/inventory/products/" + sku + "/restock")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":5}"))
        .andExpect(status().isOk());

    warmBoth(15);
  }

  @Test
  void reservingReleasingAndConfirmingEachShowAtOnce() throws Exception {
    warmBoth(10);

    reserve(4);
    warmBoth(6);

    mockMvc
        .perform(
            post("/inventory/reservations/" + orderId + "/release")
                .header("Authorization", SERVICE))
        .andExpect(status().isOk());
    warmBoth(10);

    orderId = IDS.incrementAndGet();
    reserve(3);
    warmBoth(7);
    mockMvc
        .perform(
            post("/inventory/reservations/" + orderId + "/confirm")
                .header("Authorization", SERVICE))
        .andExpect(status().isOk());
    // Confirming only settles the hold (it can no longer expire): the product row doesn't change,
    // so the numbers stay as they were.
    warmBoth(7);
  }

  @Test
  void anExpiredHoldGivesTheStockBackInTheCatalog() throws Exception {
    reserve(4);
    warmBoth(6);

    // The column is a plain TIMESTAMP holding UTC (how Hibernate stores an Instant).
    jdbc.update(
        "UPDATE reservations SET expires_at = (now() AT TIME ZONE 'UTC') - interval '1 hour'"
            + " WHERE order_id = ?",
        orderId);
    expiryJob.sweepExpiredReservations();

    warmBoth(10);
  }

  @Test
  void aChangeThatRollsBackLeavesTheCacheAlone() throws Exception {
    warmBoth(10);
    String versionBefore = redisTemplate.opsForValue().get(CatalogCache.KEY_VERSION);
    long statementsBefore = statistics().getPrepareStatementCount();

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            tx -> {
              Product product = productRepository.findBySku(sku).orElseThrow();
              product.setQuantityOnHand(999);
              productRepository.saveAndFlush(product);
              tx.setRollbackOnly();
            });

    assertThat(redisTemplate.opsForValue().get(CatalogCache.KEY_VERSION)).isEqualTo(versionBefore);
    long statementsAfterRollback = statistics().getPrepareStatementCount();
    assertThat(statementsAfterRollback).isGreaterThan(statementsBefore);
    warmBoth(10);
    assertThat(statistics().getPrepareStatementCount())
        .as("still served from the cache")
        .isEqualTo(statementsAfterRollback);
  }

  @Test
  void theCatalogKeepsWorkingWhileRedisDoesNot() throws Exception {
    warmBoth(10);

    redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
    try {
      // Reads fall through to the database (after the short Redis timeout); a write still commits.
      assertThat(availableInList()).isEqualTo(10);
      mockMvc
          .perform(
              post("/inventory/products/" + sku + "/restock")
                  .header("Authorization", ADMIN)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"quantity\":1}"))
          .andExpect(status().isOk());
      assertThat(availableBySku()).isEqualTo(11);
    } finally {
      redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
    }

    // Back again: the change made during the outage is visible without anything else happening
    // (its version token is published as soon as Redis answers).
    warmBoth(11);
  }
}
