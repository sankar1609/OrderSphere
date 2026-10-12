package com.ordersphere.audit.exception;

public class AuditEntryNotFoundException extends RuntimeException {

  public AuditEntryNotFoundException(long seq) {
    super("No audit entry with seq: " + seq);
  }
}
