package com.ordersphere.audit.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The audit queue is bound to every routing key ("#") on the shared event exchange: whatever any
 * service publishes is recorded, including event types added later.
 */
@Configuration
public class RabbitConfig {

  public static final String EVENTS_QUEUE = "audit-service.events";
  private static final String DEAD_LETTER_EXCHANGE = "ordersphere.events.dlx";
  private static final String DEAD_LETTER_QUEUE = EVENTS_QUEUE + ".dlq";

  @Bean
  public DirectExchange auditEventsDeadLetterExchange() {
    return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
  }

  @Bean
  public Queue auditEventsDeadLetterQueue() {
    return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
  }

  @Bean
  public Binding auditEventsDeadLetterBinding(
      DirectExchange auditEventsDeadLetterExchange, Queue auditEventsDeadLetterQueue) {
    return BindingBuilder.bind(auditEventsDeadLetterQueue)
        .to(auditEventsDeadLetterExchange)
        .with(DEAD_LETTER_QUEUE);
  }

  @Bean
  public Queue auditEventsQueue() {
    return QueueBuilder.durable(EVENTS_QUEUE)
        .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE)
        .build();
  }

  @Bean
  public Binding auditEventsBinding(TopicExchange domainEventsExchange, Queue auditEventsQueue) {
    return BindingBuilder.bind(auditEventsQueue).to(domainEventsExchange).with("#");
  }
}
