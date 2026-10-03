package com.ordersphere.payment.repository;

import com.ordersphere.payment.domain.Payment;
import com.ordersphere.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  Optional<Payment> findByOrderId(Long orderId);

  Optional<Payment> findByCheckoutSessionId(String checkoutSessionId);

  /** Reconciliation: the payments created in a window. */
  List<Payment> findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

  Optional<Payment> findByIdAndCustomerUsername(Long id, String customerUsername);

  List<Payment> findByStatus(PaymentStatus status);

  /** Row lock so the webhook and the reconciliation sweep can't both settle one payment. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.id = :id")
  Optional<Payment> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.checkoutSessionId = :sessionId")
  Optional<Payment> findByCheckoutSessionIdForUpdate(@Param("sessionId") String sessionId);
}
