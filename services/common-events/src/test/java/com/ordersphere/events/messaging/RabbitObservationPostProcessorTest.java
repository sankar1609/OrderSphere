package com.ordersphere.events.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class RabbitObservationPostProcessorTest {

  private final RabbitObservationPostProcessor processor = new RabbitObservationPostProcessor();

  @Test
  void enablesObservationOnTemplatesAndListenerFactories() {
    RabbitTemplate template = new RabbitTemplate(mock(ConnectionFactory.class));
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

    processor.postProcessBeforeInitialization(template, "rabbitTemplate");
    processor.postProcessBeforeInitialization(factory, "rabbitListenerContainerFactory");

    assertThat(ReflectionTestUtils.getField(template, "observationEnabled")).isEqualTo(true);
    assertThat(ReflectionTestUtils.getField(factory, "observationEnabled")).isEqualTo(true);
  }

  @Test
  void leavesOtherBeansAlone() {
    Object other = new Object();
    assertThat(processor.postProcessBeforeInitialization(other, "other")).isSameAs(other);
  }
}
