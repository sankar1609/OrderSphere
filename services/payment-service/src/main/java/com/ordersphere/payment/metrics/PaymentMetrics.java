package com.ordersphere.payment.metrics;

import com.ordersphere.payment.domain.PaymentStatus;
import com.ordersphere.payment.reconciliation.ReconciliationFinding;
import com.ordersphere.payment.reconciliation.ReconciliationFindingRepository;
import com.ordersphere.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.function.ToDoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gauges read from the database on each Prometheus scrape: payments still waiting for the customer
 * to pay, and reconciliation findings an admin hasn't settled yet. The rates of payments completing
 * or failing come from DomainEventRelay's event counter.
 */
@Component
public class PaymentMetrics implements MeterBinder {

  private static final Logger log = LoggerFactory.getLogger(PaymentMetrics.class);

  private final PaymentRepository payments;
  private final ReconciliationFindingRepository findings;

  public PaymentMetrics(PaymentRepository payments, ReconciliationFindingRepository findings) {
    this.payments = payments;
    this.findings = findings;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder(
            "ordersphere.payments.pending",
            payments,
            safely(repo -> repo.countByStatus(PaymentStatus.PENDING)))
        .description("Payments waiting for the customer to pay on the checkout page")
        .register(registry);
    Gauge.builder(
            "ordersphere.reconciliation.findings.open",
            findings,
            safely(repo -> repo.countByStatus(ReconciliationFinding.Status.OPEN)))
        .description("Mismatches with the payment provider that an admin hasn't settled")
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
