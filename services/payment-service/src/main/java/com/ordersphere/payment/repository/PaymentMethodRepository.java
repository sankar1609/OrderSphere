package com.ordersphere.payment.repository;

import com.ordersphere.payment.domain.PaymentMethod;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

  List<PaymentMethod> findByCustomerUsername(String customerUsername);

  Optional<PaymentMethod> findByIdAndCustomerUsername(Long id, String customerUsername);
}
