package com.ordersphere.audit.chain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Anchors the chain head in the application log (collected centrally by Loki). */
public class LogAuditAnchor implements AuditAnchor {

  private static final Logger log = LoggerFactory.getLogger(LogAuditAnchor.class);

  @Override
  public String target() {
    return "log";
  }

  @Override
  public void anchor(long seq, String hash) {
    log.info("AUDIT ANCHOR seq={} hash={}", seq, hash);
  }
}
