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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
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
        .convertAndSend(
            eq(EventsAutoConfiguration.EVENTS_EXCHANGE),
            eq("order.confirmed"),
            eq((Object) event),
            any(MessagePostProcessor.class));
  }

  @Test
  void swallowsBrokerFailuresRatherThanPropagating() {
    doThrow(new AmqpException("broker down"))
        .when(rabbitTemplate)
        .convertAndSend(
            anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
    DomainEventRelay relay = new DomainEventRelay(rabbitTemplate);

    relay.onEvent(new OrderConfirmedEvent(1L, "alice"));

    verify(rabbitTemplate)
        .convertAndSend(
            eq(EventsAutoConfiguration.EVENTS_EXCHANGE),
            eq("order.confirmed"),
            any(Object.class),
            any(MessagePostProcessor.class));
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
        .convertAndSend(
            anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));

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
        .convertAndSend(
            anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));

    new DomainEventRelay(rabbitTemplate).onEvent(new UserRegisteredEvent(7L, "alice", "CUSTOMER"));

    assertThat(orderIdWhileRelaying.get()).isNull();
  }

  @Test
  void countsEachRelayedEventByType() {
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    DomainEventRelay relay = new DomainEventRelay(rabbitTemplate, meters);

    relay.onEvent(new OrderConfirmedEvent(1L, "alice"));
    relay.onEvent(new OrderConfirmedEvent(2L, "bob"));
    relay.onEvent(new UserRegisteredEvent(7L, "carol", "CUSTOMER"));

    assertThat(
            meters.counter(DomainEventRelay.EVENTS_COUNTER, "type", "OrderConfirmedEvent").count())
        .isEqualTo(2.0);
    assertThat(
            meters.counter(DomainEventRelay.EVENTS_COUNTER, "type", "UserRegisteredEvent").count())
        .isEqualTo(1.0);
  }

  @Test
  void stampsTheMessageWithThePublishingServiceAndTheCaller() {
    DomainEventRelay relay =
        new DomainEventRelay(rabbitTemplate, new SimpleMeterRegistry(), "ordersphere-orders");

    Message message;
    try (var user = MDC.putCloseable("user", "alice")) {
      relay.onEvent(new OrderConfirmedEvent(1L, "alice"));
      message = postProcessed();
    }

    assertThat((Object) message.getMessageProperties().getHeader("x-source-service"))
        .isEqualTo("ordersphere-orders");
    assertThat((Object) message.getMessageProperties().getHeader("x-actor")).isEqualTo("alice");
  }

  @Test
  void leavesTheActorOffWhenNoRequestIsInProgress() {
    new DomainEventRelay(rabbitTemplate, new SimpleMeterRegistry(), "shipping-service")
        .onEvent(new OrderConfirmedEvent(1L, "alice"));

    Message message = postProcessed();
    assertThat((Object) message.getMessageProperties().getHeader("x-source-service"))
        .isEqualTo("shipping-service");
    assertThat(message.getMessageProperties().getHeaders()).doesNotContainKey("x-actor");
  }

  /** Runs the post-processor the relay handed to RabbitTemplate against an empty message. */
  private Message postProcessed() {
    ArgumentCaptor<MessagePostProcessor> processor =
        ArgumentCaptor.forClass(MessagePostProcessor.class);
    verify(rabbitTemplate)
        .convertAndSend(anyString(), anyString(), any(Object.class), processor.capture());
    return processor
        .getValue()
        .postProcessMessage(new Message(new byte[0], new MessageProperties()));
  }
}
