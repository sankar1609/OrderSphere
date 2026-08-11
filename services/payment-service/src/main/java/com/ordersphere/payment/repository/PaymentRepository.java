package com.ordersphere.payment.repository;

import com.ordersphere.payment.domain.Payment;
import com.ordersphere.payment.domain.PaymentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

  Optional<Payment> findByOrderId(Long orderId);

  Optional<Payment> findByIdAndCustomerUsername(Long id, String customerUsername);

  List<Payment> findByStatus(PaymentStatus status);
}
