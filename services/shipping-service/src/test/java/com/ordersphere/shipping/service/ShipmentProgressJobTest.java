package com.ordersphere.shipping.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ordersphere.shipping.domain.Shipment;
import com.ordersphere.shipping.domain.ShipmentStatus;
import com.ordersphere.shipping.domain.ShipmentType;
import com.ordersphere.shipping.repository.ShipmentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ShipmentProgressJobTest {

  @Mock private ShipmentRepository shipmentRepository;
  @Mock private ShipmentService shipmentService;

  @Test
  void advancePendingShipmentsAdvancesEachNonDeliveredShipment() {
    Shipment pending = new Shipment(100L, ShipmentType.OUTBOUND, "123 Main St", null);
    when(shipmentRepository.findByStatusNot(ShipmentStatus.DELIVERED)).thenReturn(List.of(pending));

    ShipmentProgressJob job = new ShipmentProgressJob(shipmentRepository, shipmentService);
    job.advancePendingShipments();

    verify(shipmentService).advance(pending);
  }

  @Test
  void advancePendingShipmentsDoesNothingWhenNonePending() {
    when(shipmentRepository.findByStatusNot(ShipmentStatus.DELIVERED)).thenReturn(List.of());

    ShipmentProgressJob job = new ShipmentProgressJob(shipmentRepository, shipmentService);
    job.advancePendingShipments();

    verifyNoInteractions(shipmentService);
  }
}
