package com.ordersphere.shipping.repository;

import com.ordersphere.shipping.domain.Shipment;
import com.ordersphere.shipping.domain.ShipmentStatus;
import com.ordersphere.shipping.domain.ShipmentType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

  Optional<Shipment> findByOrderIdAndType(Long orderId, ShipmentType type);

  List<Shipment> findByOrderId(Long orderId);

  Optional<Shipment> findByIdAndCustomerUsername(Long id, String customerUsername);

  List<Shipment> findByOrderIdAndCustomerUsername(Long orderId, String customerUsername);

  List<Shipment> findByStatusNotIn(Collection<ShipmentStatus> statuses);
}
