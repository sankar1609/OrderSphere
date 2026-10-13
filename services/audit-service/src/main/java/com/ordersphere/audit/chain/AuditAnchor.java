package com.ordersphere.audit.chain;

/**
 * Somewhere outside this database that the chain head is written to, so that rewriting the whole
 * chain here (which would verify, on its own) still disagrees with a record this database's
 * administrator can't change. Today: the application log, which is shipped to Loki ({@link
 * LogAuditAnchor}). The intended next step is a ledger shared with other organizations (e.g. a
 * Hyperledger Fabric channel) - implement this interface and declare it as a bean.
 */
public interface AuditAnchor {

  /** A short name for where the anchor goes, recorded with each anchor. */
  String target();

  /** Publishes "entry {@code seq} has hash {@code hash}". May throw; the job retries next run. */
  void anchor(long seq, String hash);
}
