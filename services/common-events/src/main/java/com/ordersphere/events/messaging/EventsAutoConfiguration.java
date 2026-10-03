package com.ordersphere.events.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Wires the shared "ordersphere.events" topic exchange, a Jackson message converter that can
 * deserialize the common-events POJOs (no no-args constructors, so ParameterNamesModule + the
 * maven.compiler.parameters=true build flag are both required), and the relay that publishes every
 * locally-raised BaseEvent to that exchange.
 */
@AutoConfiguration
public class EventsAutoConfiguration {

  public static final String EVENTS_EXCHANGE = "ordersphere.events";

  @Bean
  @ConditionalOnMissingBean
  public TopicExchange domainEventsExchange() {
    return new TopicExchange(EVENTS_EXCHANGE, true, false);
  }

  @Bean
  @ConditionalOnMissingBean(MessageConverter.class)
  public MessageConverter domainEventMessageConverter() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new ParameterNamesModule());
    mapper.registerModule(new JavaTimeModule());
    return new Jackson2JsonMessageConverter(mapper);
  }

  /** Static: a BeanPostProcessor must exist before the beans it processes. */
  @Bean
  public static RabbitObservationPostProcessor rabbitObservationPostProcessor() {
    return new RabbitObservationPostProcessor();
  }

  @Bean
  @ConditionalOnMissingBean
  public DomainEventRelay domainEventRelay(RabbitTemplate rabbitTemplate) {
    return new DomainEventRelay(rabbitTemplate);
  }
}
