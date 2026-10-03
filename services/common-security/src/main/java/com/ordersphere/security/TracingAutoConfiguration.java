package com.ordersphere.security;

import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Registers {@link TraceIdResponseFilter} in services that have tracing on the classpath. */
@AutoConfiguration
@ConditionalOnClass(Tracer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TracingAutoConfiguration {

  @Bean
  public FilterRegistrationBean<TraceIdResponseFilter> traceIdResponseFilter(
      ObjectProvider<Tracer> tracer) {
    FilterRegistrationBean<TraceIdResponseFilter> registration =
        new FilterRegistrationBean<>(new TraceIdResponseFilter(tracer));
    // After ServerHttpObservationFilter (HIGHEST_PRECEDENCE + 1), which opens the span; before
    // Spring Security (-100).
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }
}
