package com.ordersphere.audit.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.audit.domain.RecordedEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

/**
 * Turns a raw broker message into a {@link RecordedEvent}. Deliberately generic: it reads the JSON
 * as a tree rather than binding to the event classes, so an event type this service has never heard
 * of is still recorded - and a message that isn't even JSON is recorded too, as-is. An audit log
 * that drops what it can't parse would be hiding exactly the unusual things.
 */
@Component
public class RecordedEventMapper {

  static final String HEADER_SOURCE_SERVICE = "x-source-service";
  static final String HEADER_ACTOR = "x-actor";
  private static final String HEADER_TRACEPARENT = "traceparent";
  private static final Pattern TRACEPARENT = Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-.*");

  /** Whose business the event is, in order of preference, from the event payloads' fields. */
  private static final String[] SUBJECT_FIELDS = {
    "customerUsername", "username", "recipientUsername", "ownerUsername"
  };

  private final ObjectMapper objectMapper = new ObjectMapper();

  public RecordedEvent map(Message message, Instant receivedAt) {
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);
    Map<String, Object> headers = message.getMessageProperties().getHeaders();
    String routingKey = message.getMessageProperties().getReceivedRoutingKey();

    JsonNode json = parse(payload);
    String eventId = text(json, "eventId");
    String eventType = text(json, "eventType");
    Instant occurredAt = instant(json == null ? null : json.get("timestamp"));

    return new RecordedEvent(
        // Without an id of its own, the same bytes are the same event.
        eventId != null ? eventId : "sha256:" + sha256(routingKey + "\n" + payload),
        eventType != null ? eventType : "Unknown",
        routingKey,
        string(headers.get(HEADER_SOURCE_SERVICE)),
        string(headers.get(HEADER_ACTOR)),
        longValue(json, "orderId"),
        subject(json),
        traceId(string(headers.get(HEADER_TRACEPARENT))),
        (occurredAt != null ? occurredAt : receivedAt).truncatedTo(ChronoUnit.MILLIS),
        payload);
  }

  private JsonNode parse(String payload) {
    try {
      JsonNode node = objectMapper.readTree(payload);
      return node != null && node.isObject() ? node : null;
    } catch (Exception ex) {
      return null;
    }
  }

  private static String text(JsonNode json, String field) {
    JsonNode value = json == null ? null : json.get(field);
    return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
  }

  private static Long longValue(JsonNode json, String field) {
    JsonNode value = json == null ? null : json.get(field);
    return value != null && value.canConvertToLong() ? value.asLong() : null;
  }

  private static String subject(JsonNode json) {
    for (String field : SUBJECT_FIELDS) {
      String value = text(json, field);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  /** Jackson writes an Instant as seconds with a fraction (the services' setting) or ISO-8601. */
  private static Instant instant(JsonNode value) {
    try {
      if (value == null || value.isNull()) {
        return null;
      }
      if (value.isNumber()) {
        return Instant.ofEpochMilli(
            value
                .decimalValue()
                .movePointRight(3)
                .setScale(0, java.math.RoundingMode.DOWN)
                .longValueExact());
      }
      return Instant.parse(value.asText());
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private static String traceId(String traceparent) {
    if (traceparent == null) {
      return null;
    }
    Matcher matcher = TRACEPARENT.matcher(traceparent);
    return matcher.matches() ? matcher.group(1) : null;
  }

  private static String string(Object header) {
    if (header == null) {
      return null;
    }
    String value = header.toString();
    return value.isBlank() ? null : value.length() > 100 ? value.substring(0, 100) : value;
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required", ex);
    }
  }
}
