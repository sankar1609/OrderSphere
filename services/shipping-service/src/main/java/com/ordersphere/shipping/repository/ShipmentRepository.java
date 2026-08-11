package com.ordersphere.shipping.repository;

import com.ordersphere.shipping.domain.Shipment;
import com.ordersphere.shipping.domain.ShipmentStatus;
import com.ordersphere.shipping.domain.ShipmentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

  Optional<Shipment> findByOrderIdAndType(Long orderId, ShipmentType type);

  List<Shipment> findByOrderId(Long orderId);

  List<Shipment> findByStatusNot(ShipmentStatus status);
}
