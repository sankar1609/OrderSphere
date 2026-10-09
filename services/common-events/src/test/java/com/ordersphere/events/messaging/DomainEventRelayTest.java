package com.ordersphere.events.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.ordersphere.events.OrderConfirmedEvent;
import com.ordersphere.events.UserRegisteredEvent;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
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

  @Test
  void tagsAnOrderEventWithItsOrderIdWhileRelayingIt() {
    AtomicReference<String> orderIdWhileRelaying = new AtomicReference<>();
    doAnswer(
            invocation -> {
              orderIdWhileRelaying.set(MDC.get(DomainEventRelay.MDC_ORDER_ID));
              return null;
            })
        .when(rabbitTemplate)
        .convertAndSend(anyString(), anyString(), any(Object.class));

    new DomainEventRelay(rabbitTemplate).onEvent(new OrderConfirmedEvent(42L, "alice"));

    assertThat(orderIdWhileRelaying.get()).isEqualTo("42");
    assertThat(MDC.get(DomainEventRelay.MDC_ORDER_ID)).isNull();
  }

  @Test
  void keepsAnOrderIdTheCallerAlreadySet() {
    try (var outer = MDC.putCloseable(DomainEventRelay.MDC_ORDER_ID, "42")) {
      new DomainEventRelay(rabbitTemplate).onEvent(new OrderConfirmedEvent(42L, "alice"));

      assertThat(MDC.get(DomainEventRelay.MDC_ORDER_ID)).isEqualTo("42");
    }
  }

  @Test
  void leavesTheLogContextAloneForEventsWithoutAnOrder() {
    AtomicReference<String> orderIdWhileRelaying = new AtomicReference<>("unset");
    doAnswer(
            invocation -> {
              orderIdWhileRelaying.set(MDC.get(DomainEventRelay.MDC_ORDER_ID));
              return null;
            })
        .when(rabbitTemplate)
        .convertAndSend(anyString(), anyString(), any(Object.class));

    new DomainEventRelay(rabbitTemplate).onEvent(new UserRegisteredEvent(7L, "alice", "CUSTOMER"));

    assertThat(orderIdWhileRelaying.get()).isNull();
  }
}
