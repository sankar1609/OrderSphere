package com.ordersphere.audit.chain;

import java.time.Instant;

/**
 * The outcome of walking the whole chain. When {@code valid} is false, {@code firstBrokenSeq} is
 * the first entry (or anchor) at which the stored chain stops matching, and {@code reason} says
 * how.
 */
public record VerificationResult(
    boolean valid,
    long entriesChecked,
    Long firstBrokenSeq,
    String reason,
    long headSeq,
    String headHash,
    Instant verifiedAt) {

  static VerificationResult ok(long checked, long headSeq, String headHash, Instant at) {
    return new VerificationResult(true, checked, null, null, headSeq, headHash, at);
  }

  static VerificationResult broken(
      long checked, long seq, String reason, long headSeq, String headHash, Instant at) {
    return new VerificationResult(false, checked, seq, reason, headSeq, headHash, at);
  }
}
