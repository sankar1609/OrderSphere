package com.ordersphere.orders.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ordersphere.orders.exception.CompensationCallException;
import com.ordersphere.orders.exception.InventoryReservationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class InventoryClientTest {

  @Test
  void reserveSendsExpectedRequestAndForwardsToken() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    InventoryClient client = new InventoryClient(builder);

    server
        .expect(requestTo("http://inventory-service/inventory/reservations"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer token"))
        .andExpect(jsonPath("$.orderId").value(1))
        .andExpect(jsonPath("$.items[0].sku").value("SKU-1"))
        .andRespond(
            withSuccess(
                """
                {"orderId": 1, "status": "ACTIVE",
                 "reserved": [{"sku": "SKU-1", "quantity": 1, "unitPrice": 9.99}],
                 "backordered": [{"sku": "SKU-1", "quantity": 1, "unitPrice": 9.99}]}
                """,
                MediaType.APPLICATION_JSON));

    InventoryClient.ReserveResponse response =
        client.reserve(
            1L, List.of(new InventoryClient.ReserveRequest.Item("SKU-1", 2)), "Bearer token");

    server.verify();
    assertThat(response.reserved())
        .containsExactly(
            new InventoryClient.ReserveResponse.LineItem("SKU-1", 1, new BigDecimal("9.99")));
    assertThat(response.backordered()).hasSize(1);
  }

  @Test
  void reserveThrowsInventoryReservationExceptionOnFailure() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    InventoryClient client = new InventoryClient(builder);

    server
        .expect(requestTo("http://inventory-service/inventory/reservations"))
        .andRespond(withServerError());

    assertThatThrownBy(
            () ->
                client.reserve(
                    1L,
                    List.of(new InventoryClient.ReserveRequest.Item("SKU-1", 2)),
                    "Bearer token"))
        .isInstanceOf(InventoryReservationException.class);
  }

  @Test
  void confirmSendsExpectedRequestAndForwardsToken() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    InventoryClient client = new InventoryClient(builder);

    server
        .expect(requestTo("http://inventory-service/inventory/reservations/1/confirm"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer token"))
        .andRespond(withSuccess());

    client.confirm(1L, "Bearer token");

    server.verify();
  }

  @Test
  void confirmDoesNotSwallowFailures() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    InventoryClient client = new InventoryClient(builder);

    server
        .expect(requestTo("http://inventory-service/inventory/reservations/1/confirm"))
        .andRespond(withStatus(HttpStatus.CONFLICT));

    assertThatThrownBy(() -> client.confirm(1L, "Bearer token"))
        .isInstanceOf(InventoryReservationException.class)
        .hasCauseInstanceOf(HttpClientErrorException.class);
  }

  @Test
  void releaseSendsExpectedRequestAndReportsRetryableFailures() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    InventoryClient client = new InventoryClient(builder);

    server
        .expect(requestTo("http://inventory-service/inventory/reservations/1/release"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer token"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> client.release(1L, "Bearer token"))
        .isInstanceOfSatisfying(
            CompensationCallException.class, ex -> assertThat(ex.isRetryable()).isTrue());
    server.verify();
  }

  @Test
  void releaseRejectionIsNotRetryable() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    InventoryClient client = new InventoryClient(builder);
    server
        .expect(requestTo("http://inventory-service/inventory/reservations/1/release"))
        .andRespond(withStatus(HttpStatus.CONFLICT));

    assertThatThrownBy(() -> client.release(1L, "Bearer token"))
        .isInstanceOfSatisfying(
            CompensationCallException.class,
            ex -> {
              assertThat(ex.isRetryable()).isFalse();
              assertThat(ex.getStatus()).isEqualTo(409);
            });
  }

  @Test
  void reserveFallbackRethrowsInventoryReservationExceptionUnchanged() {
    InventoryClient client = new InventoryClient(RestClient.builder());
    InventoryReservationException original = new InventoryReservationException("boom");

    assertThatThrownBy(
            () ->
                client.reserveFallback(
                    1L,
                    List.of(new InventoryClient.ReserveRequest.Item("SKU-1", 2)),
                    "Bearer token",
                    original))
        .isSameAs(original);
  }

  @Test
  void reserveFallbackWrapsCircuitBreakerExceptionAsInventoryReservationException() {
    InventoryClient client = new InventoryClient(RestClient.builder());

    assertThatThrownBy(
            () ->
                client.reserveFallback(
                    1L,
                    List.of(new InventoryClient.ReserveRequest.Item("SKU-1", 2)),
                    "Bearer token",
                    new RuntimeException("circuit breaker open")))
        .isInstanceOf(InventoryReservationException.class);
  }

  @Test
  void releaseFallbackReportsAnOpenBreakerAsRetryable() {
    InventoryClient client = new InventoryClient(RestClient.builder());

    assertThatThrownBy(
            () ->
                client.releaseFallback(
                    1L, "Bearer token", new RuntimeException("circuit breaker open")))
        .isInstanceOfSatisfying(
            CompensationCallException.class, ex -> assertThat(ex.isRetryable()).isTrue());
  }
}
