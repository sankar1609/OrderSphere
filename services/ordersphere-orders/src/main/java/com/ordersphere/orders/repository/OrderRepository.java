package com.ordersphere.orders.repository;

import com.ordersphere.orders.domain.Order;
import com.ordersphere.orders.domain.OrderStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findByCustomerUsername(String customerUsername);

  Optional<Order> findByIdAndCustomerUsername(Long id, String customerUsername);

  List<Order> findByStatus(OrderStatus status);
}
