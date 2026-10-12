package com.ordersphere.orders.metrics;

import com.ordersphere.orders.domain.Compensation;
import com.ordersphere.orders.domain.OrderStatus;
import com.ordersphere.orders.repository.CompensationRepository;
import com.ordersphere.orders.repository.OrderRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.function.ToDoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gauges for the order saga's current state, read from the database on each Prometheus scrape:
 * orders per status, and the backlog that needs watching - refunds/stock releases still owed
 * (PENDING) or stuck (FAILED), and paid orders without a shipment. The rates of things happening
 * come from DomainEventRelay's event counter; these are "how many right now".
 */
@Component
public class OrderMetrics implements MeterBinder {

  private static final Logger log = LoggerFactory.getLogger(OrderMetrics.class);

  private final OrderRepository orders;
  private final CompensationRepository compensations;

  public OrderMetrics(OrderRepository orders, CompensationRepository compensations) {
    this.orders = orders;
    this.compensations = compensations;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    for (OrderStatus status : OrderStatus.values()) {
      Gauge.builder("ordersphere.orders", orders, safely(repo -> repo.countByStatus(status)))
          .description("Orders currently in each status")
          .tag("status", status.name())
          .register(registry);
    }
    for (Compensation.Status status :
        new Compensation.Status[] {Compensation.Status.PENDING, Compensation.Status.FAILED}) {
      Gauge.builder(
              "ordersphere.compensations",
              compensations,
              safely(repo -> repo.countByStatus(status)))
          .description("Refunds and stock releases still owed (PENDING) or stuck (FAILED)")
          .tag("status", status.name())
          .register(registry);
    }
    Gauge.builder(
            "ordersphere.orders.unshipped",
            orders,
            safely(
                repo ->
                    repo.countByStatusAndShipmentIdIsNullAndShipmentNextAttemptAtIsNotNull(
                        OrderStatus.CONFIRMED)))
        .description("Paid orders without a shipment")
        .tag("state", "retrying")
        .register(registry);
    Gauge.builder(
            "ordersphere.orders.unshipped",
            orders,
            safely(
                repo ->
                    repo.countByStatusAndShipmentIdIsNullAndShipmentNextAttemptAtIsNull(
                        OrderStatus.CONFIRMED)))
        .description("Paid orders without a shipment")
        .tag("state", "gave_up")
        .register(registry);
  }

  /** A scrape mustn't fail because the database is briefly unreachable: report "no value". */
  private static <T> ToDoubleFunction<T> safely(ToDoubleFunction<T> count) {
    return target -> {
      try {
        return count.applyAsDouble(target);
      } catch (RuntimeException ex) {
        log.debug("Metric not available: {}", ex.getMessage());
        return Double.NaN;
      }
    };
  }
}
