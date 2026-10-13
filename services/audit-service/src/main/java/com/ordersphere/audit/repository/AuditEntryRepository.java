package com.ordersphere.audit.repository;

import com.ordersphere.audit.domain.AuditEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditEntryRepository
    extends JpaRepository<AuditEntry, Long>, JpaSpecificationExecutor<AuditEntry> {

  boolean existsByEventId(String eventId);

  List<AuditEntry> findByOrderIdOrderBySeqAsc(Long orderId);

  /** The next stretch of the chain after {@code seq}, in order - how verification walks it. */
  List<AuditEntry> findTop500BySeqGreaterThanOrderBySeqAsc(long seq);
}
