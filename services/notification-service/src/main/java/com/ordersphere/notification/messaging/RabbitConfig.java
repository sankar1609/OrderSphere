package com.ordersphere.notification.messaging;

import com.ordersphere.events.DeliveryConfirmedEvent;
import com.ordersphere.events.OrderCancelledEvent;
import com.ordersphere.events.OrderConfirmedEvent;
import com.ordersphere.events.PaymentCompletedEvent;
import com.ordersphere.events.PaymentFailedEvent;
import com.ordersphere.events.ShipmentCreatedEvent;
import com.ordersphere.events.ShipmentInTransitEvent;
import com.ordersphere.events.ShipmentPickedEvent;
import com.ordersphere.events.StockLowEvent;
import com.ordersphere.events.messaging.RoutingKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Durable queue realizing "notification-service consumes all major events" - bound to exactly the
 * event types that map to an existing TemplateKey (order confirmed/cancelled, payment
 * completed/failed, and every shipment milestone: created, picked, in transit, delivered).
 * Shipping-service's Shipment rows carry a denormalized customerUsername (see shipping-service's V2
 * migration) specifically so shipment events can address a notification the same way every other
 * bound event here does - a plain payload read, no synchronous lookup back into another service.
 */
@Configuration
public class RabbitConfig {

  public static final String EVENTS_QUEUE = "notification-service.events";
  private static final String DEAD_LETTER_EXCHANGE = "ordersphere.events.dlx";
  private static final String DEAD_LETTER_QUEUE = EVENTS_QUEUE + ".dlq";

  @Bean
  public DirectExchange notificationEventsDeadLetterExchange() {
    return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
  }

  @Bean
  public Queue notificationEventsDeadLetterQueue() {
    return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
  }

  @Bean
  public Binding notificationEventsDeadLetterBinding(
      DirectExchange notificationEventsDeadLetterExchange,
      Queue notificationEventsDeadLetterQueue) {
    return BindingBuilder.bind(notificationEventsDeadLetterQueue)
        .to(notificationEventsDeadLetterExchange)
        .with(DEAD_LETTER_QUEUE);
  }

  @Bean
  public Queue notificationEventsQueue() {
    return QueueBuilder.durable(EVENTS_QUEUE)
        .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE)
        .build();
  }

  @Bean
  public Declarables notificationEventsBindings(
      TopicExchange domainEventsExchange, Queue notificationEventsQueue) {
    return new Declarables(
        bindingFor(domainEventsExchange, notificationEventsQueue, OrderConfirmedEvent.class),
        bindingFor(domainEventsExchange, notificationEventsQueue, OrderCancelledEvent.class),
        bindingFor(domainEventsExchange, notificationEventsQueue, PaymentCompletedEvent.class),
        bindingFor(domainEventsExchange, notificationEventsQueue, PaymentFailedEvent.class),
        bindingFor(domainEventsExchange, notificationEventsQueue, ShipmentCreatedEvent.class),
        bindingFor(domainEventsExchange, notificationEventsQueue, ShipmentPickedEvent.class),
        bindingFor(domainEventsExchange, notificationEventsQueue, ShipmentInTransitEvent.class),
        bindingFor(domainEventsExchange, notificationEventsQueue, DeliveryConfirmedEvent.class),
        bindingFor(domainEventsExchange, notificationEventsQueue, StockLowEvent.class));
  }

  private static Binding bindingFor(TopicExchange exchange, Queue queue, Class<?> eventType) {
    return BindingBuilder.bind(queue)
        .to(exchange)
        .with(RoutingKeys.forEventType(eventType.getSimpleName()));
  }
}
