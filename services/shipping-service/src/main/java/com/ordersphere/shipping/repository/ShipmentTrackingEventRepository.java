package com.ordersphere.shipping.repository;

import com.ordersphere.shipping.domain.ShipmentTrackingEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentTrackingEventRepository
    extends JpaRepository<ShipmentTrackingEvent, Long> {

  List<ShipmentTrackingEvent> findByShipmentIdOrderByOccurredAtAsc(Long shipmentId);
}
