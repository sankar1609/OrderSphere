package com.ordersphere.orders.config;

import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

  /**
   * Without a request-factory timeout, a hung downstream call blocks a saga thread indefinitely and
   * never resolves to a failure - so the circuit breakers wrapping these clients (see
   * InventoryClient/PaymentClient/ShippingClient) can never see it and never open. Values are kept
   * comfortably below the breakers' wait-duration-in-open-state (15-20s) so a hung call fails fast
   * enough to actually feed the failure-rate calculation.
   */
  @Bean
  @LoadBalanced
  public RestClient.Builder loadBalancedRestClientBuilder(
      @Value("${orders.rest-client.connect-timeout-ms}") long connectTimeoutMs,
      @Value("${orders.rest-client.read-timeout-ms}") long readTimeoutMs,
      ObjectProvider<ObservationRegistry> observationRegistry) {
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            .withReadTimeout(Duration.ofMillis(readTimeoutMs));
    ClientHttpRequestFactory factory = ClientHttpRequestFactories.get(settings);
    // Built by hand (it's load-balanced), so it doesn't get Boot's observation customizer: without
    // the registry, calls to inventory/payment/shipping would carry no traceparent and every trace
    // would stop at orders.
    return RestClient.builder()
        .requestFactory(factory)
        .observationRegistry(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP));
  }
}
