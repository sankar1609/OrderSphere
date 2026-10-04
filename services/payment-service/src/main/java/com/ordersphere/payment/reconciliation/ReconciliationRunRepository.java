package com.ordersphere.payment.reconciliation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {

  List<ReconciliationRun> findTop20ByOrderByIdDesc();
}
