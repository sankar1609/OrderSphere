package com.ordersphere.payment.reconciliation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationFindingRepository
    extends JpaRepository<ReconciliationFinding, Long> {

  Optional<ReconciliationFinding> findByTypeAndCheckoutSessionIdAndStatus(
      ReconciliationFinding.Type type,
      String checkoutSessionId,
      ReconciliationFinding.Status status);

  List<ReconciliationFinding> findByStatus(ReconciliationFinding.Status status);

  Page<ReconciliationFinding> findByStatus(ReconciliationFinding.Status status, Pageable pageable);

  long countByStatus(ReconciliationFinding.Status status);

  boolean existsByTypeAndCheckoutSessionIdAndResolution(
      ReconciliationFinding.Type type,
      String checkoutSessionId,
      ReconciliationFinding.Resolution resolution);
}
