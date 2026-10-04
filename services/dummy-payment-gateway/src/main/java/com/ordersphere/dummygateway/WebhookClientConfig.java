package com.ordersphere.dummygateway;

import java.time.Duration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Webhook delivery timeouts. Without them, posting to a merchant that's down waited for the OS's
 * TCP timeout (about ten minutes). Applied first, so a test's mock server customizer still wins.
 */
@Configuration
public class WebhookClientConfig {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public RestClientCustomizer webhookTimeouts() {
    return builder ->
        builder.requestFactory(
            ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                    .withConnectTimeout(Duration.ofSeconds(2))
                    .withReadTimeout(Duration.ofSeconds(5))));
  }
}
