package com.ordersphere.inventory.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ordersphere.inventory.repository.ProductRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class InventoryMetricsTest {

  @Mock private ProductRepository products;

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void reportsHowManyProductsAreLowOnStock() {
    when(products.countLowStock()).thenReturn(7L);
    new InventoryMetrics(products).bindTo(registry);

    assertThat(registry.get("ordersphere.products.low.stock").gauge().value()).isEqualTo(7.0);
  }

  @Test
  void reportsNoValueRatherThanFailingTheScrapeWhenTheDatabaseIsDown() {
    when(products.countLowStock())
        .thenThrow(new DataAccessResourceFailureException("connection refused"));
    new InventoryMetrics(products).bindTo(registry);

    assertThat(registry.get("ordersphere.products.low.stock").gauge().value()).isNaN();
  }
}
