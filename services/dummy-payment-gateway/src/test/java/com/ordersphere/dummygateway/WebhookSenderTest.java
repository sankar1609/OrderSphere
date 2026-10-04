package com.ordersphere.dummygateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class WebhookSenderTest {

  @Test
  void sendingReturnsAtOnceAndDeliversInTheBackground() {
    List<Runnable> queued = new ArrayList<>();
    WebhookSender sender =
        new WebhookSender(
            RestClient.builder(),
            new ObjectMapper(),
            new GatewayProperties("http://gw", "key", "secret", Duration.ofMinutes(10)),
            queued::add);
    CheckoutSession session =
        new CheckoutSession(
            "cs_1",
            "42",
            new BigDecimal("6.00"),
            "USD",
            "Order",
            "http://ok",
            "http://cancel",
            // An unreachable merchant: if send() delivered inline, this would block the caller.
            "http://10.255.255.1/webhook",
            Instant.now(),
            Instant.now().plusSeconds(600));

    sender.send(session, "checkout.session.completed");

    assertThat(queued).hasSize(1); // handed to the executor, not sent on the caller's thread
  }

  @Test
  void aSessionWithoutAWebhookUrlSendsNothing() {
    List<Runnable> queued = new ArrayList<>();
    WebhookSender sender =
        new WebhookSender(
            RestClient.builder(),
            new ObjectMapper(),
            new GatewayProperties("http://gw", "key", "secret", Duration.ofMinutes(10)),
            queued::add);
    CheckoutSession session =
        new CheckoutSession(
            "cs_2",
            "42",
            BigDecimal.ONE,
            "USD",
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now().plusSeconds(600));

    sender.send(session, "checkout.session.completed");

    assertThat(queued).isEmpty();
  }
}
