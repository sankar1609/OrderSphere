package com.ordersphere.audit.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordersphere.audit.domain.RecordedEvent;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class RecordedEventMapperTest {

  private static final Instant RECEIVED = Instant.parse("2026-10-12T09:00:00.999999Z");

  private final RecordedEventMapper mapper = new RecordedEventMapper();

  private static Message message(String body, String routingKey) {
    MessageProperties properties = new MessageProperties();
    properties.setReceivedRoutingKey(routingKey);
    return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
  }

  @Test
  void readsTheEventAndItsHeaders() {
    String body =
        "{\"eventId\":\"e-1\",\"eventType\":\"PaymentCompletedEvent\",\"timestamp\":1791792000.123456789,"
            + "\"paymentId\":7,\"orderId\":42,\"customerUsername\":\"alice\",\"amount\":39.98}";
    Message message = message(body, "payment.completed");
    message.getMessageProperties().setHeader("x-source-service", "payment-service");
    message.getMessageProperties().setHeader("x-actor", "orders-service");
    message
        .getMessageProperties()
        .setHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

    RecordedEvent event = mapper.map(message, RECEIVED);

    assertThat(event.eventId()).isEqualTo("e-1");
    assertThat(event.eventType()).isEqualTo("PaymentCompletedEvent");
    assertThat(event.routingKey()).isEqualTo("payment.completed");
    assertThat(event.sourceService()).isEqualTo("payment-service");
    assertThat(event.actor()).isEqualTo("orders-service");
    assertThat(event.orderId()).isEqualTo(42L);
    assertThat(event.subjectUsername()).isEqualTo("alice");
    assertThat(event.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    // Jackson's seconds-with-fraction, truncated to the millisecond the hash covers.
    assertThat(event.occurredAt()).isEqualTo(Instant.ofEpochMilli(1791792000123L));
    assertThat(event.payload()).isEqualTo(body);
  }

  @Test
  void acceptsAnIsoTimestampAndEventsWithoutAnOrderOrActor() {
    RecordedEvent event =
        mapper.map(
            message(
                "{\"eventId\":\"e-2\",\"eventType\":\"UserRegisteredEvent\","
                    + "\"timestamp\":\"2026-10-12T08:30:00.5Z\",\"userId\":9,\"username\":\"bob\"}",
                "user.registered"),
            RECEIVED);

    assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-10-12T08:30:00.500Z"));
    assertThat(event.orderId()).isNull();
    assertThat(event.actor()).isNull();
    assertThat(event.sourceService()).isNull();
    assertThat(event.traceId()).isNull();
    assertThat(event.subjectUsername()).isEqualTo("bob");
  }

  @Test
  void recordsAnEventTypeItHasNeverHeardOf() {
    RecordedEvent event =
        mapper.map(
            message(
                "{\"eventId\":\"e-3\",\"eventType\":\"SomethingNewEvent\",\"timestamp\":1791792000,"
                    + "\"ownerUsername\":\"vera\",\"whatever\":{\"nested\":true}}",
                "something.new"),
            RECEIVED);

    assertThat(event.eventType()).isEqualTo("SomethingNewEvent");
    assertThat(event.subjectUsername()).isEqualTo("vera");
  }

  @Test
  void stillRecordsAMessageThatIsNotAnEventAtAll() {
    RecordedEvent first = mapper.map(message("not json", "odd.key"), RECEIVED);
    RecordedEvent again = mapper.map(message("not json", "odd.key"), RECEIVED.plusSeconds(60));
    RecordedEvent other = mapper.map(message("also not json", "odd.key"), RECEIVED);

    assertThat(first.eventType()).isEqualTo("Unknown");
    assertThat(first.payload()).isEqualTo("not json");
    assertThat(first.occurredAt()).isEqualTo(Instant.parse("2026-10-12T09:00:00.999Z"));
    // The id is derived from the content, so a redelivery is recognised as the same message.
    assertThat(first.eventId()).startsWith("sha256:").isEqualTo(again.eventId());
    assertThat(other.eventId()).isNotEqualTo(first.eventId());
  }
}
