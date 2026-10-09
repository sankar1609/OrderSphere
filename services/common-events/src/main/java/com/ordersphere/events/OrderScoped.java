package com.ordersphere.events;

/**
 * An event about one order. {@link com.ordersphere.events.messaging.DomainEventRelay} uses it to
 * tag the event's log line with the order id, so an order's history can be followed in the logs
 * across every service that publishes about it.
 */
public interface OrderScoped {

  Long getOrderId();
}
