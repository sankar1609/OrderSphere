package com.ordersphere.events.messaging;

import com.ordersphere.events.BaseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(DomainEventRelay.class);

  private final RabbitTemplate rabbitTemplate;

  public DomainEventRelay(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  @EventListener
  public void onEvent(BaseEvent event) {
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
