package com.ordersphere.events.messaging;

import java.util.Locale;

/**
 * Derives AMQP routing keys from a BaseEvent's eventType, e.g. "PaymentCompletedEvent" -&gt;
 * "payment.completed".
 */
public final class RoutingKeys {

  private static final String EVENT_SUFFIX = "Event";

  private RoutingKeys() {}

  public static String forEventType(String eventType) {
    String withoutSuffix =
        eventType.endsWith(EVENT_SUFFIX)
            ? eventType.substring(0, eventType.length() - EVENT_SUFFIX.length())
            : eventType;
    return withoutSuffix.replaceAll("(?<!^)([A-Z])", ".$1").toLowerCase(Locale.ROOT);
  }
}
