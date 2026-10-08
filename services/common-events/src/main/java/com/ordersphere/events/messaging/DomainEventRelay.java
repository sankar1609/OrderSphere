package com.ordersphere.events.messaging;

import com.ordersphere.events.BaseEvent;
import com.ordersphere.events.OrderScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.event.EventListener;

/**
 * Forwards every locally-published BaseEvent to the shared RabbitMQ exchange. Registered
 * automatically for any service that depends on common-events (see EventsAutoConfiguration), so
 * none of the existing ApplicationEventPublisher.publishEvent(...) call sites need to change.
 * BaseEvent is a plain POJO rather than a Spring ApplicationEvent, so this listens via
 * {@literal @}EventListener (which Spring wraps as a PayloadApplicationEvent) rather than
 * implementing ApplicationListener&lt;BaseEvent&gt;, which the type bound doesn't allow.
 */
public class DomainEventRelay {

  /** Same key the services use for their own order-scoped work (e.g. OrderLogContext). */
  public static final String MDC_ORDER_ID = "orderId";

  private static final Logger log = LoggerFactory.getLogger(DomainEventRelay.class);

  private final RabbitTemplate rabbitTemplate;

  public DomainEventRelay(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  /**
   * Relays the event and logs it - one INFO line per state change, in every service. An event about
   * an order also carries the order id in the logging context (MDC key {@value #MDC_ORDER_ID})
   * unless the caller already set it, so the order can be searched for in the central logs.
   */
  @EventListener
  public void onEvent(BaseEvent event) {
    Long orderId = event instanceof OrderScoped scoped ? scoped.getOrderId() : null;
    boolean tagOrder = orderId != null && MDC.get(MDC_ORDER_ID) == null;
    if (tagOrder) {
      MDC.put(MDC_ORDER_ID, String.valueOf(orderId));
    }
    try {
      relay(event, orderId);
    } finally {
      if (tagOrder) {
        MDC.remove(MDC_ORDER_ID);
      }
    }
  }

  private void relay(BaseEvent event, Long orderId) {
    if (orderId != null) {
      log.info("{} for orderId {} (eventId={})", event.getEventType(), orderId, event.getEventId());
    } else {
      log.info("{} (eventId={})", event.getEventType(), event.getEventId());
    }
    String routingKey = RoutingKeys.forEventType(event.getEventType());
    try {
      rabbitTemplate.convertAndSend(EventsAutoConfiguration.EVENTS_EXCHANGE, routingKey, event);
    } catch (Exception ex) {
      log.warn(
          "Failed to relay {} (eventId={}) to the event exchange: {}",
          event.getEventType(),
          event.getEventId(),
          ex.getMessage());
    }
  }
}
