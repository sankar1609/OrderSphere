package com.ordersphere.events.messaging;

import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Turns on Micrometer observation for every RabbitTemplate and listener container factory, so a
 * trace continues from the publishing service to the consuming one (the context travels in the
 * message headers). Done in code because Spring Boot 3.2 has no {@code
 * spring.rabbitmq.*.observation-enabled} properties (they arrive in 3.3). Harmless where tracing
 * isn't on: observation then goes to a no-op registry.
 */
public class RabbitObservationPostProcessor implements BeanPostProcessor {

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if (bean instanceof RabbitTemplate template) {
      template.setObservationEnabled(true);
    } else if (bean instanceof AbstractRabbitListenerContainerFactory<?> factory) {
      factory.setObservationEnabled(true);
    }
    return bean;
  }
}
