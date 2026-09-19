package com.ordersphere.orders.repository;

import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findByCustomerUsername(String customerUsername);

  Optional<Order> findByIdAndCustomerUsername(Long id, String customerUsername);

  List<Order> findByStatus(OrderStatus status);

  /**
   * Row-locking read used to serialize the two independent paths that can progress an
   * AWAITING_PAYMENT order (the PaymentCompletedEvent listener and the saga sweep job's polling)
   * so they can't race each other into a lost update or a duplicate OrderConfirmedEvent.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id = :id")
  Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
