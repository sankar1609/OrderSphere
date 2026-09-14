package com.ordersphere.shipping.messaging;

import com.ordersphere.events.OrderCancelledEvent;
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
 * Durable queue that lets this service halt an in-flight shipment when its order is cancelled -
 * without it, ShipmentProgressJob has no idea an order was cancelled and keeps advancing the
 * shipment to DELIVERED regardless.
 */
@Configuration
public class RabbitConfig {

  public static final String ORDER_EVENTS_QUEUE = "shipping-service.order-events";
  private static final String DEAD_LETTER_EXCHANGE = "ordersphere.events.dlx";
  private static final String DEAD_LETTER_QUEUE = ORDER_EVENTS_QUEUE + ".dlq";

  @Bean
  public DirectExchange orderEventsDeadLetterExchange() {
    return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
  }

  @Bean
  public Queue orderEventsDeadLetterQueue() {
    return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
  }

  @Bean
  public Binding orderEventsDeadLetterBinding(
      DirectExchange orderEventsDeadLetterExchange, Queue orderEventsDeadLetterQueue) {
    return BindingBuilder.bind(orderEventsDeadLetterQueue)
        .to(orderEventsDeadLetterExchange)
        .with(DEAD_LETTER_QUEUE);
  }

  @Bean
  public Queue orderEventsQueue() {
    return QueueBuilder.durable(ORDER_EVENTS_QUEUE)
        .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE)
        .build();
  }

  @Bean
  public Declarables orderEventsBindings(
      TopicExchange domainEventsExchange, Queue orderEventsQueue) {
    Binding cancelled =
        BindingBuilder.bind(orderEventsQueue)
            .to(domainEventsExchange)
            .with(RoutingKeys.forEventType(OrderCancelledEvent.class.getSimpleName()));
    return new Declarables(cancelled);
  }
}
