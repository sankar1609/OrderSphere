package com.ordersphere.audit.metrics;

import com.ordersphere.audit.chain.AuditVerifier;
import com.ordersphere.audit.chain.VerificationResult;
import com.ordersphere.audit.repository.AuditEntryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

/**
 * {@code ordersphere_audit_entries}: how long the chain is. {@code ordersphere_audit_chain_valid}:
 * 1 if the last verification passed, 0 if it found a break (the AuditChainBroken alert), no value
 * until the first verification has run.
 */
@Component
public class AuditMetrics implements MeterBinder {

  private final AuditEntryRepository entries;
  private final AuditVerifier verifier;

  public AuditMetrics(AuditEntryRepository entries, AuditVerifier verifier) {
    this.entries = entries;
    this.verifier = verifier;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("ordersphere.audit.entries", entries, this::count)
        .description("Entries in the audit chain")
        .register(registry);
    Gauge.builder("ordersphere.audit.chain.valid", verifier, this::valid)
        .description("1 if the last verification of the audit chain passed, 0 if it found a break")
        .register(registry);
  }

  /** A scrape mustn't fail because the database is briefly unreachable: report "no value". */
  private double count(AuditEntryRepository repo) {
    try {
      return repo.count();
    } catch (RuntimeException ex) {
      return Double.NaN;
    }
  }

  private double valid(AuditVerifier source) {
    VerificationResult last = source.lastResult();
    if (last == null) {
      return Double.NaN;
    }
    return last.valid() ? 1 : 0;
  }
}
