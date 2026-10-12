package com.ordersphere.audit.chain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodic verification and anchoring. Neither may fail the scheduler: errors are logged. */
@Component
public class AuditJobs {

  private static final Logger log = LoggerFactory.getLogger(AuditJobs.class);

  private final AuditVerifier verifier;
  private final AuditAnchorService anchors;

  public AuditJobs(AuditVerifier verifier, AuditAnchorService anchors) {
    this.verifier = verifier;
    this.anchors = anchors;
  }

  /** Also runs shortly after startup, so the chain's state is known without waiting an interval. */
  @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${audit.verify.interval}")
  public void verify() {
    try {
      verifier.verify();
    } catch (RuntimeException ex) {
      log.warn("Audit chain verification could not run: {}", ex.getMessage());
    }
  }

  @Scheduled(initialDelayString = "PT60S", fixedDelayString = "${audit.anchor.interval}")
  public void anchor() {
    try {
      anchors.anchorHead();
    } catch (RuntimeException ex) {
      log.warn("Audit chain head could not be anchored, will retry: {}", ex.getMessage());
    }
  }
}
