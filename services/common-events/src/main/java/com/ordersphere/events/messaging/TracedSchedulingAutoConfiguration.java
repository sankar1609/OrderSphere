package com.ordersphere.events.messaging;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

/**
 * Makes each run of an {@code @Scheduled} sweep (saga progress, reservation expiry, payment
 * reconciliation, shipment progress, notification delivery) its own trace, so the REST calls and
 * events it causes are grouped under it instead of appearing as unrelated root spans. Only applies
 * where scheduling is enabled and an ObservationRegistry (tracing) is present.
 */
@AutoConfiguration
@ConditionalOnClass(ObservationRegistry.class)
public class TracedSchedulingAutoConfiguration {

  @Bean
  public SchedulingConfigurer observedScheduledTasks(ObjectProvider<ObservationRegistry> registry) {
    return taskRegistrar -> registry.ifAvailable(taskRegistrar::setObservationRegistry);
  }
}
