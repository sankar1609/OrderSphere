package com.ordersphere.payment.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ordersphere.payment.domain.PaymentStatus;
import com.ordersphere.payment.reconciliation.ReconciliationFinding;
import com.ordersphere.payment.reconciliation.ReconciliationFindingRepository;
import com.ordersphere.payment.repository.PaymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class PaymentMetricsTest {

  @Mock private PaymentRepository payments;
  @Mock private ReconciliationFindingRepository findings;

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void reportsPendingPaymentsAndOpenReconciliationFindings() {
    when(payments.countByStatus(PaymentStatus.PENDING)).thenReturn(5L);
    when(findings.countByStatus(ReconciliationFinding.Status.OPEN)).thenReturn(2L);
    new PaymentMetrics(payments, findings).bindTo(registry);

    assertThat(registry.get("ordersphere.payments.pending").gauge().value()).isEqualTo(5.0);
    assertThat(registry.get("ordersphere.reconciliation.findings.open").gauge().value())
        .isEqualTo(2.0);
  }

  @Test
  void reportsNoValueRatherThanFailingTheScrapeWhenTheDatabaseIsDown() {
    when(payments.countByStatus(PaymentStatus.PENDING))
        .thenThrow(new DataAccessResourceFailureException("connection refused"));
    new PaymentMetrics(payments, findings).bindTo(registry);

    assertThat(registry.get("ordersphere.payments.pending").gauge().value()).isNaN();
  }
}
