package com.ordersphere.audit.web;

import com.ordersphere.audit.chain.VerificationResult;
import java.time.Instant;

/** Where the chain stands: how long it is, when it was last verified and last anchored. */
public record AuditStatusResponse(
    long entries,
    long headSeq,
    String headHash,
    VerificationResult lastVerification,
    Anchor lastAnchor) {

  public record Anchor(long seq, String hash, String target, Instant anchoredAt) {}
}
