package com.ordersphere.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Registers {@link MetricsScrapeTokenFilter} in every servlet service: the Prometheus endpoint only
 * answers a scrape that carries {@code METRICS_SCRAPE_TOKEN}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MetricsAutoConfiguration {

  @Bean
  public FilterRegistrationBean<MetricsScrapeTokenFilter> metricsScrapeTokenFilter(
      @Value("${ordersphere.metrics.scrape-token:${METRICS_SCRAPE_TOKEN:}}") String scrapeToken) {
    FilterRegistrationBean<MetricsScrapeTokenFilter> registration =
        new FilterRegistrationBean<>(new MetricsScrapeTokenFilter(scrapeToken));
    // Before Spring Security (-100), which merely permits the path.
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return registration;
  }
}
