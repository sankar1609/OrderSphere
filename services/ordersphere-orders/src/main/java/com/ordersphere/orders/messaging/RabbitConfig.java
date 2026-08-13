package com.ordersphere.orders.messaging;

import com.ordersphere.events.PaymentCompletedEvent;
import com.ordersphere.events.PaymentFailedEvent;
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
 * Durable queue that lets this service react to payment outcomes directly off the broker, instead
 * of relying solely on OrderSagaProgressJob's poll + paymentClient.getStatus() REST call.
 */
@Configuration
public class RabbitConfig {

  public static final String PAYMENT_EVENTS_QUEUE = "ordersphere-orders.payment-events";
  private static final String DEAD_LETTER_EXCHANGE = "ordersphere.events.dlx";
  private static final String DEAD_LETTER_QUEUE = PAYMENT_EVENTS_QUEUE + ".dlq";

  @Bean
  public DirectExchange paymentEventsDeadLetterExchange() {
    return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
  }

  @Bean
  public Queue paymentEventsDeadLetterQueue() {
    return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
  }

  @Bean
  public Binding paymentEventsDeadLetterBinding(
      DirectExchange paymentEventsDeadLetterExchange, Queue paymentEventsDeadLetterQueue) {
    return BindingBuilder.bind(paymentEventsDeadLetterQueue)
        .to(paymentEventsDeadLetterExchange)
        .with(DEAD_LETTER_QUEUE);
  }

  @Bean
  public Queue paymentEventsQueue() {
    return QueueBuilder.durable(PAYMENT_EVENTS_QUEUE)
        .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE)
        .build();
  }

  @Bean
  public Declarables paymentEventsBindings(
      TopicExchange domainEventsExchange, Queue paymentEventsQueue) {
    Binding completed =
        BindingBuilder.bind(paymentEventsQueue)
            .to(domainEventsExchange)
            .with(RoutingKeys.forEventType(PaymentCompletedEvent.class.getSimpleName()));
    Binding failed =
        BindingBuilder.bind(paymentEventsQueue)
            .to(domainEventsExchange)
            .with(RoutingKeys.forEventType(PaymentFailedEvent.class.getSimpleName()));
    return new Declarables(completed, failed);
  }
}
