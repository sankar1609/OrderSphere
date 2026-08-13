package com.ordersphere.events.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.ordersphere.events.OrderConfirmedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class DomainEventRelayTest {

  @Mock private RabbitTemplate rabbitTemplate;

  @Test
  void relaysEventToExchangeWithDerivedRoutingKey() {
    DomainEventRelay relay = new DomainEventRelay(rabbitTemplate);
    OrderConfirmedEvent event = new OrderConfirmedEvent(1L, "alice");

    relay.onEvent(event);

    verify(rabbitTemplate)
        .convertAndSend(EventsAutoConfiguration.EVENTS_EXCHANGE, "order.confirmed", event);
  }

  @Test
  void swallowsBrokerFailuresRatherThanPropagating() {
    doThrow(new AmqpException("broker down"))
        .when(rabbitTemplate)
        .convertAndSend(anyString(), anyString(), any(Object.class));
    DomainEventRelay relay = new DomainEventRelay(rabbitTemplate);

    relay.onEvent(new OrderConfirmedEvent(1L, "alice"));

    verify(rabbitTemplate)
        .convertAndSend(
            eq(EventsAutoConfiguration.EVENTS_EXCHANGE), eq("order.confirmed"), any(Object.class));
  }
}
