package com.ordersphere.inventory.repository;

import com.ordersphere.inventory.domain.Reservation;
import com.ordersphere.inventory.domain.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

  Optional<Reservation> findByOrderId(Long orderId);

  List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant instant);
}
