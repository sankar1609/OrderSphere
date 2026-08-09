package com.ordersphere.inventory.repository;

import com.ordersphere.inventory.domain.Backorder;
import com.ordersphere.inventory.domain.BackorderStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackorderRepository extends JpaRepository<Backorder, Long> {

  List<Backorder> findByProductIdAndStatusOrderByCreatedAtAsc(
      Long productId, BackorderStatus status);

  List<Backorder> findByOrderIdAndStatus(Long orderId, BackorderStatus status);
}
