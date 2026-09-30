package com.ordersphere.orders.repository;

import com.ordersphere.orders.domain.Compensation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompensationRepository extends JpaRepository<Compensation, Long> {

  @Query(
      "select c.id from Compensation c where c.status = 'PENDING' and c.nextAttemptAt <= :now"
          + " order by c.nextAttemptAt")
  List<Long> findDueIds(@Param("now") Instant now);

  /**
   * Claims a pending compensation for one attempt. SKIP LOCKED: if another thread (the post-commit
   * attempt vs the sweep) is already working on it, this one just skips it instead of waiting.
   */
  @Query(
      value =
          "select * from order_compensations where id = :id and status = 'PENDING'"
              + " for update skip locked",
      nativeQuery = true)
  Optional<Compensation> claim(@Param("id") Long id);

  List<Compensation> findByOrderIdOrderById(Long orderId);
}
