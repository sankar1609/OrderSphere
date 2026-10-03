package com.ordersphere.payment.reconciliation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily check of the last window (default 02:00, the previous day) against the provider. */
@Component
public class ReconciliationJob {

  private final ReconciliationService reconciliation;
  private final Duration window;
  private final Clock clock = Clock.systemUTC();

  public ReconciliationJob(
      ReconciliationService reconciliation,
      @Value("${payment.reconciliation.window:P1D}") Duration window) {
    this.reconciliation = reconciliation;
    this.window = window;
  }

  @Scheduled(cron = "${payment.reconciliation.cron:0 0 2 * * *}")
  public void reconcileLastWindow() {
    Instant now = clock.instant();
    reconciliation.run(now.minus(window), now);
  }
}
