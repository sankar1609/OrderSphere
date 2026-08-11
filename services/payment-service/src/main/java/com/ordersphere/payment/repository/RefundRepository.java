package com.ordersphere.payment.repository;

import com.ordersphere.payment.domain.Refund;
import com.ordersphere.payment.domain.RefundStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {

  Optional<Refund> findByPaymentId(Long paymentId);

  List<Refund> findByStatus(RefundStatus status);
}
