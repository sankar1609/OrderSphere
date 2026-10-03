package com.ordersphere.orders.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class RestClientConfigTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void readTimeoutStopsAHungCallBeforeTheServerResponds() throws Exception {
    long readTimeoutMs = 500;
    long serverDelayMs = 5000;

    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/slow",
        exchange -> {
          try {
            Thread.sleep(serverDelayMs);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          exchange.sendResponseHeaders(200, 0);
          exchange.close();
        });
    server.start();

    RestClient.Builder builder =
        new RestClientConfig().loadBalancedRestClientBuilder(2000, readTimeoutMs, noRegistry());
    RestClient client =
        builder.baseUrl("http://localhost:" + server.getAddress().getPort()).build();

    Instant start = Instant.now();

    assertThatThrownBy(() -> client.get().uri("/slow").retrieve().toBodilessEntity())
        .isInstanceOf(ResourceAccessException.class);

    Duration elapsed = Duration.between(start, Instant.now());
    assertThat(elapsed).isLessThan(Duration.ofMillis(serverDelayMs));
  }

  private static ObjectProvider<ObservationRegistry> noRegistry() {
    return new StaticListableBeanFactory().getBeanProvider(ObservationRegistry.class);
  }

  @Test
  void callsGoThroughTheObservationRegistrySoTheTraceIsPropagated() throws Exception {
    AtomicReference<String> received = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/ping",
        exchange -> {
          received.set(exchange.getRequestHeaders().getFirst("traceparent"));
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();

    // Stands in for the tracing handler, which injects the current trace into the outgoing request
    // the same way (the context's setter writes into the request headers).
    ObservationRegistry registry = ObservationRegistry.create();
    registry
        .observationConfig()
        .observationHandler(
            new ObservationHandler<ClientRequestObservationContext>() {
              @Override
              public void onStart(ClientRequestObservationContext context) {
                context.getSetter().set(context.getCarrier(), "traceparent", "00-trace-span-01");
              }

              @Override
              public boolean supportsContext(Observation.Context context) {
                return context instanceof ClientRequestObservationContext;
              }
            });
    StaticListableBeanFactory beans = new StaticListableBeanFactory();
    beans.addBean("observationRegistry", registry);

    RestClient client =
        new RestClientConfig()
            .loadBalancedRestClientBuilder(
                2000, 2000, beans.getBeanProvider(ObservationRegistry.class))
            .baseUrl("http://localhost:" + server.getAddress().getPort())
            .build();
    client.get().uri("/ping").retrieve().toBodilessEntity();

    assertThat(received.get()).isEqualTo("00-trace-span-01");
  }
}
