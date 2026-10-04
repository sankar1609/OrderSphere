package com.ordersphere.dummygateway;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from CheckoutSession s where s.id = :id")
  Optional<CheckoutSession> findByIdForUpdate(@Param("id") String id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from CheckoutSession s where s.chargeReference = :chargeReference")
  Optional<CheckoutSession> findByChargeReferenceForUpdate(
      @Param("chargeReference") String chargeReference);

  List<CheckoutSession> findByChargedAtGreaterThanEqualAndChargedAtLessThanOrderByChargedAt(
      Instant from, Instant to);

  List<CheckoutSession> findByRefundedAtGreaterThanEqualAndRefundedAtLessThanOrderByRefundedAt(
      Instant from, Instant to);
}
