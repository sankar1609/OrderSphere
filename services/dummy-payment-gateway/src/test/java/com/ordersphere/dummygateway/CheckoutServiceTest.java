package com.ordersphere.dummygateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CheckoutServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

  private final WebhookSender webhookSender = mock(WebhookSender.class);
  private MutableClock clock;
  private CheckoutService service;

  @BeforeEach
  void setUp() {
    clock = new MutableClock(NOW);
    service =
        new CheckoutService(
            new GatewayProperties("http://gw", "key", "secret", Duration.ofMinutes(10)),
            webhookSender,
            clock);
  }

  private CheckoutSession session() {
    return service.create(
        "42",
        new BigDecimal("6.00"),
        "USD",
        "Order #7",
        "http://ok",
        "http://cancel",
        "http://hook");
  }

  @Test
  void successCardCompletesSessionAndNotifiesMerchant() {
    CheckoutSession session = session();

    CheckoutService.PayResult result =
        service.pay(session, "4242 4242 4242 4242", "12/30", "123", "Alice");

    assertThat(result.outcome()).isEqualTo(CheckoutService.PayResult.Outcome.SUCCEEDED);
    assertThat(service.status(session)).isEqualTo(CheckoutSession.Status.SUCCEEDED);
    assertThat(session.getChargeReference()).startsWith("ch_");
    verify(webhookSender).send(session, "checkout.session.completed");
  }

  @Test
  void declinedCardKeepsSessionOpenForAnotherAttempt() {
    CheckoutSession session = session();

    CheckoutService.PayResult declined =
        service.pay(session, "4000000000000002", "12/30", "123", "Alice");
    CheckoutService.PayResult retried =
        service.pay(session, "4242424242424242", "12/30", "123", "Alice");

    assertThat(declined.outcome()).isEqualTo(CheckoutService.PayResult.Outcome.REJECTED);
    assertThat(declined.message()).contains("declined");
    assertThat(retried.outcome()).isEqualTo(CheckoutService.PayResult.Outcome.SUCCEEDED);
  }

  @Test
  void invalidInputIsRejectedWithoutCharging() {
    CheckoutSession session = session();

    assertThat(service.pay(session, "4242424242424241", "12/30", "123", "A").message())
        .contains("card number");
    assertThat(service.pay(session, "4242424242424242", "01/20", "123", "A").message())
        .contains("expiry");
    assertThat(service.pay(session, "4242424242424242", "12/30", "1", "A").message())
        .contains("security code");
    assertThat(service.pay(session, "4242424242424242", "12/30", "123", " ").message())
        .contains("name");
    assertThat(service.status(session)).isEqualTo(CheckoutSession.Status.OPEN);
    verify(webhookSender, never()).send(any(), any());
  }

  @Test
  void cannotPayTwice() {
    CheckoutSession session = session();
    service.pay(session, "4242424242424242", "12/30", "123", "Alice");

    CheckoutService.PayResult second =
        service.pay(session, "4242424242424242", "12/30", "123", "Alice");

    assertThat(second.outcome()).isEqualTo(CheckoutService.PayResult.Outcome.NOT_PAYABLE);
    verify(webhookSender).send(eq(session), eq("checkout.session.completed"));
  }

  @Test
  void cancelCompletesSessionOnceAndNotifiesMerchant() {
    CheckoutSession session = session();

    assertThat(service.cancel(session)).isTrue();
    assertThat(service.cancel(session)).isFalse();
    assertThat(service.status(session)).isEqualTo(CheckoutSession.Status.CANCELLED);
    verify(webhookSender).send(session, "checkout.session.cancelled");
  }

  @Test
  void sessionExpiresAfterTtlAndCanNoLongerBePaid() {
    CheckoutSession session = session();
    clock.advance(Duration.ofMinutes(11));

    CheckoutService.PayResult result =
        service.pay(session, "4242424242424242", "12/30", "123", "Alice");

    assertThat(service.status(session)).isEqualTo(CheckoutSession.Status.EXPIRED);
    assertThat(result.outcome()).isEqualTo(CheckoutService.PayResult.Outcome.NOT_PAYABLE);
  }

  @Test
  void refundIsIdempotentPerChargeAndUnknownChargesAreNotFound() {
    CheckoutSession session = session();
    service.pay(session, "4242424242424242", "12/30", "123", "Alice");

    String first = service.refund(session.getChargeReference()).orElseThrow();
    String second = service.refund(session.getChargeReference()).orElseThrow();

    assertThat(first).startsWith("re_").isEqualTo(second);
    assertThat(service.refund("ch_unknown")).isEmpty();
  }

  @Test
  void luhnCheck() {
    assertThat(CheckoutService.passesLuhn("4242424242424242")).isTrue();
    assertThat(CheckoutService.passesLuhn("4000000000000002")).isTrue();
    assertThat(CheckoutService.passesLuhn("4242424242424241")).isFalse();
  }

  private static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
