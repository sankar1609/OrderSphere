package com.ordersphere.shipping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.events.DeliveryConfirmedEvent;
import com.ordersphere.events.ShipmentCreatedEvent;
import com.ordersphere.events.ShipmentInTransitEvent;
import com.ordersphere.events.ShipmentPickedEvent;
import com.ordersphere.shipping.carrier.CarrierClient;
import com.ordersphere.shipping.carrier.CarrierUpdate;
import com.ordersphere.shipping.domain.Shipment;
import com.ordersphere.shipping.domain.ShipmentStatus;
import com.ordersphere.shipping.domain.ShipmentType;
import com.ordersphere.shipping.dto.CreateShipmentRequest;
import com.ordersphere.shipping.dto.ReturnShipmentRequest;
import com.ordersphere.shipping.dto.ShipmentResponse;
import com.ordersphere.shipping.exception.InvalidShipmentStateException;
import com.ordersphere.shipping.exception.ShipmentNotFoundException;
import com.ordersphere.shipping.repository.ShipmentRepository;
import com.ordersphere.shipping.repository.ShipmentTrackingEventRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

  @Mock private ShipmentRepository shipmentRepository;
  @Mock private ShipmentTrackingEventRepository trackingEventRepository;
  @Mock private CarrierClient carrierClient;
  @Mock private ApplicationEventPublisher eventPublisher;

  private ShipmentService shipmentService;

  @BeforeEach
  void setUp() {
    shipmentService =
        new ShipmentService(
            shipmentRepository, trackingEventRepository, carrierClient, eventPublisher);
    shipmentService.self = shipmentService;
  }

  @Test
  void createShipmentCreatesAndPublishesEvent() {
    when(shipmentRepository.findByOrderIdAndType(100L, ShipmentType.OUTBOUND))
        .thenReturn(Optional.empty());

    ShipmentResponse response =
        shipmentService.createShipment(new CreateShipmentRequest(100L, "alice", "123 Main St"));

    assertThat(response.status()).isEqualTo(ShipmentStatus.CREATED);
    assertThat(response.type()).isEqualTo(ShipmentType.OUTBOUND);
    verify(eventPublisher).publishEvent(any(ShipmentCreatedEvent.class));
  }

  @Test
  void createShipmentIsIdempotentPerOrderId() {
    Shipment existing = new Shipment(100L, "alice", ShipmentType.OUTBOUND, "123 Main St", null);
    when(shipmentRepository.findByOrderIdAndType(100L, ShipmentType.OUTBOUND))
        .thenReturn(Optional.of(existing));

    ShipmentResponse response =
        shipmentService.createShipment(new CreateShipmentRequest(100L, "alice", "123 Main St"));

    assertThat(response.orderId()).isEqualTo(100L);
    verify(eventPublisher, never()).publishEvent(any(ShipmentCreatedEvent.class));
  }

  @Test
  void createShipmentFallsBackToExistingShipmentOnConcurrentConflict() {
    Shipment winner = new Shipment(100L, "alice", ShipmentType.OUTBOUND, "123 Main St", null);
    when(shipmentRepository.findByOrderIdAndType(100L, ShipmentType.OUTBOUND))
        .thenReturn(Optional.empty(), Optional.of(winner));
    when(shipmentRepository.save(any(Shipment.class)))
        .thenThrow(
            new DataIntegrityViolationException("duplicate key: uq_shipments_order_id_type"));

    ShipmentResponse response =
        shipmentService.createShipment(new CreateShipmentRequest(100L, "alice", "123 Main St"));

    assertThat(response.orderId()).isEqualTo(100L);
    assertThat(response.type()).isEqualTo(ShipmentType.OUTBOUND);
    verify(eventPublisher, never()).publishEvent(any(ShipmentCreatedEvent.class));
  }

  @Test
  void requestReturnRequiresDeliveredShipment() {
    Shipment shipment = new Shipment(100L, "alice", ShipmentType.OUTBOUND, "123 Main St", null);
    shipment.setId(5L);
    when(shipmentRepository.findByIdAndCustomerUsername(5L, "alice"))
        .thenReturn(Optional.of(shipment));

    assertThatThrownBy(
            () ->
                shipmentService.requestReturn(
                    "alice", false, 5L, new ReturnShipmentRequest("wrong size")))
        .isInstanceOf(InvalidShipmentStateException.class);
  }

  @Test
  void requestReturnIsIdempotent() {
    Shipment shipment = new Shipment(100L, "alice", ShipmentType.OUTBOUND, "123 Main St", null);
    shipment.setId(5L);
    shipment.advanceTo(ShipmentStatus.PICKED);
    shipment.advanceTo(ShipmentStatus.IN_TRANSIT);
    shipment.advanceTo(ShipmentStatus.DELIVERED);
    Shipment existingReturn = new Shipment(100L, "alice", ShipmentType.RETURN, "123 Main St", 5L);
    when(shipmentRepository.findByIdAndCustomerUsername(5L, "alice"))
        .thenReturn(Optional.of(shipment));
    when(shipmentRepository.findByOrderIdAndType(100L, ShipmentType.RETURN))
        .thenReturn(Optional.of(existingReturn));

    ShipmentResponse response =
        shipmentService.requestReturn("alice", false, 5L, new ReturnShipmentRequest("wrong size"));

    assertThat(response.type()).isEqualTo(ShipmentType.RETURN);
    verify(eventPublisher, never()).publishEvent(any(ShipmentCreatedEvent.class));
  }

  @Test
  void requestReturnFallsBackToExistingReturnOnConcurrentConflict() {
    Shipment shipment = new Shipment(100L, "alice", ShipmentType.OUTBOUND, "123 Main St", null);
    shipment.setId(5L);
    shipment.advanceTo(ShipmentStatus.PICKED);
    shipment.advanceTo(ShipmentStatus.IN_TRANSIT);
    shipment.advanceTo(ShipmentStatus.DELIVERED);
    Shipment winnerReturn = new Shipment(100L, "alice", ShipmentType.RETURN, "123 Main St", 5L);
    when(shipmentRepository.findByIdAndCustomerUsername(5L, "alice"))
        .thenReturn(Optional.of(shipment));
    when(shipmentRepository.findByOrderIdAndType(100L, ShipmentType.RETURN))
        .thenReturn(Optional.empty(), Optional.of(winnerReturn));
    when(shipmentRepository.save(any(Shipment.class)))
        .thenThrow(
            new DataIntegrityViolationException("duplicate key: uq_shipments_order_id_type"));

    ShipmentResponse response =
        shipmentService.requestReturn("alice", false, 5L, new ReturnShipmentRequest("wrong size"));

    assertThat(response.type()).isEqualTo(ShipmentType.RETURN);
    verify(eventPublisher, never()).publishEvent(any(ShipmentCreatedEvent.class));
  }

  @Test
  void customerCannotSeeAnotherCustomersShipment() {
    when(shipmentRepository.findByIdAndCustomerUsername(5L, "mallory"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> shipmentService.getShipment("mallory", false, 5L))
        .isInstanceOf(ShipmentNotFoundException.class);
    assertThatThrownBy(() -> shipmentService.getTracking("mallory", false, 5L))
        .isInstanceOf(ShipmentNotFoundException.class);
    assertThatThrownBy(
            () ->
                shipmentService.requestReturn(
                    "mallory", false, 5L, new ReturnShipmentRequest("not mine")))
        .isInstanceOf(ShipmentNotFoundException.class);
    verify(shipmentRepository, never()).findById(any());
  }

  @Test
  void adminCanSeeAnyShipment() {
    Shipment shipment = new Shipment(100L, "alice", ShipmentType.OUTBOUND, "123 Main St", null);
    shipment.setId(5L);
    when(shipmentRepository.findById(5L)).thenReturn(Optional.of(shipment));

    assertThat(shipmentService.getShipment("admin", true, 5L).orderId()).isEqualTo(100L);
  }

  @Test
  void listByOrderOnlyReturnsTheCallersShipments() {
    when(shipmentRepository.findByOrderIdAndCustomerUsername(100L, "mallory"))
        .thenReturn(List.of());

    assertThat(shipmentService.listByOrder("mallory", false, 100L)).isEmpty();
    verify(shipmentRepository, never()).findByOrderId(any());
  }

  @Test
  void advanceMovesCreatedToPickedAndPublishesEvent() {
    Shipment shipment = new Shipment(100L, "alice", ShipmentType.OUTBOUND, "123 Main St", null);
    when(carrierClient.nextStage(ShipmentStatus.CREATED))
        .thenReturn(new CarrierUpdate(ShipmentStatus.PICKED, "Origin facility"));

    shipmentService.advance(shipment);

    assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PICKED);
    verify(eventPublisher).publishEvent(any(ShipmentPickedEvent.class));
  }

  @Test
  void advanceMovesInTransitToDeliveredAndPublishesConfirmation() {
    Shipment shipment = new Shipment(100L, "alice", ShipmentType.OUTBOUND, "123 Main St", null);
    shipment.advanceTo(ShipmentStatus.PICKED);
    shipment.advanceTo(ShipmentStatus.IN_TRANSIT);
    when(carrierClient.nextStage(ShipmentStatus.IN_TRANSIT))
        .thenReturn(new CarrierUpdate(ShipmentStatus.DELIVERED, "Destination"));

    shipmentService.advance(shipment);

    assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    assertThat(shipment.getDeliveredAt()).isNotNull();
    verify(eventPublisher).publishEvent(any(DeliveryConfirmedEvent.class));
  }

  @Test
  void advanceOnDeliveredShipmentIsNoOp() {
    Shipment shipment = new Shipment(100L, "alice", ShipmentType.OUTBOUND, "123 Main St", null);
    shipment.advanceTo(ShipmentStatus.PICKED);
    shipment.advanceTo(ShipmentStatus.IN_TRANSIT);
    shipment.advanceTo(ShipmentStatus.DELIVERED);

    shipmentService.advance(shipment);

    verify(carrierClient, never()).nextStage(any());
    verify(eventPublisher, never()).publishEvent(any(ShipmentInTransitEvent.class));
  }

  @Test
  void advanceOnCancelledShipmentIsNoOp() {
    Shipment shipment = new Shipment(100L, "alice", ShipmentType.OUTBOUND, "123 Main St", null);
    shipment.advanceTo(ShipmentStatus.CANCELLED);

    shipmentService.advance(shipment);

    verify(carrierClient, never()).nextStage(any());
  }

  @Test
  void cancelForOrderHaltsInFlightShipment() {
    Shipment shipment = new Shipment(100L, "alice", ShipmentType.OUTBOUND, "123 Main St", null);
    shipment.advanceTo(ShipmentStatus.PICKED);
    when(shipmentRepository.findByOrderIdAndType(100L, ShipmentType.OUTBOUND))
        .thenReturn(Optional.of(shipment));

    shipmentService.cancelForOrder(100L);

    assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
    verify(shipmentRepository).save(shipment);
  }

  @Test
  void cancelForOrderIsNoOpWhenAlreadyDelivered() {
    Shipment shipment = new Shipment(100L, "alice", ShipmentType.OUTBOUND, "123 Main St", null);
    shipment.advanceTo(ShipmentStatus.PICKED);
    shipment.advanceTo(ShipmentStatus.IN_TRANSIT);
    shipment.advanceTo(ShipmentStatus.DELIVERED);
    when(shipmentRepository.findByOrderIdAndType(100L, ShipmentType.OUTBOUND))
        .thenReturn(Optional.of(shipment));

    shipmentService.cancelForOrder(100L);

    assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    verify(shipmentRepository, never()).save(any());
  }

  @Test
  void cancelForOrderIsNoOpWhenNoShipmentExists() {
    when(shipmentRepository.findByOrderIdAndType(100L, ShipmentType.OUTBOUND))
        .thenReturn(Optional.empty());

    shipmentService.cancelForOrder(100L);

    verify(shipmentRepository, never()).save(any());
  }
}
