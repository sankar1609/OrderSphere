package com.ordersphere.orders.logging;

import org.slf4j.MDC;

/**
 * Puts an order's id into the logging context (MDC key {@value #ORDER_ID}), so every line logged
 * while working on that order - in JSON logs, a searchable field in Loki - can be found by order,
 * across the request, the saga sweep, payment events, compensations and shipment retries.
 *
 * <p>Use it at the entry point of each unit of work on one order, not in methods those call:
 * closing removes the key, so a nested scope would clear it for the rest of the outer one.
 */
public final class OrderLogContext {

  public static final String ORDER_ID = "orderId";

  private OrderLogContext() {}

  public static MDC.MDCCloseable forOrder(Long orderId) {
    return MDC.putCloseable(ORDER_ID, String.valueOf(orderId));
  }
}
