package com.ordersphere.shipping.service;

import com.ordersphere.shipping.domain.ShipmentStatus;
import com.ordersphere.shipping.repository.ShipmentRepository;
import java.util.EnumSet;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShipmentProgressJob {

  private static final EnumSet<ShipmentStatus> TERMINAL_STATUSES =
      EnumSet.of(ShipmentStatus.DELIVERED, ShipmentStatus.CANCELLED);

  private final ShipmentRepository shipmentRepository;
  private final ShipmentService shipmentService;

  public ShipmentProgressJob(
      ShipmentRepository shipmentRepository, ShipmentService shipmentService) {
    this.shipmentRepository = shipmentRepository;
    this.shipmentService = shipmentService;
  }

  @Scheduled(fixedDelayString = "${shipping.progress.sweep-interval-ms}")
  public void sweep() {
    advancePendingShipments();
  }

  public void advancePendingShipments() {
    shipmentRepository.findByStatusNotIn(TERMINAL_STATUSES).forEach(shipmentService::advance);
  }
}
