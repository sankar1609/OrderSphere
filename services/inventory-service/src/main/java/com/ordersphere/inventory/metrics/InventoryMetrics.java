package com.ordersphere.inventory.metrics;

import com.ordersphere.inventory.repository.ProductRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gauge read from the database on each Prometheus scrape: how many products are at or below their
 * reorder threshold right now. (StockLowEvent, counted by DomainEventRelay, is the moment one
 * crosses it.)
 */
@Component
public class InventoryMetrics implements MeterBinder {

  private static final Logger log = LoggerFactory.getLogger(InventoryMetrics.class);

  private final ProductRepository products;

  public InventoryMetrics(ProductRepository products) {
    this.products = products;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("ordersphere.products.low.stock", products, this::lowStock)
        .description("Products at or below their reorder threshold")
        .register(registry);
  }

  /** A scrape mustn't fail because the database is briefly unreachable: report "no value". */
  private double lowStock(ProductRepository repo) {
    try {
      return repo.countLowStock();
    } catch (RuntimeException ex) {
      log.debug("Metric not available: {}", ex.getMessage());
      return Double.NaN;
    }
  }
}
