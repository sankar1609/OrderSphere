package com.ordersphere.shipping.service;

import com.ordersphere.events.DeliveryConfirmedEvent;
import com.ordersphere.events.ShipmentCreatedEvent;
import com.ordersphere.events.ShipmentInTransitEvent;
import com.ordersphere.events.ShipmentPickedEvent;
import com.ordersphere.shipping.carrier.CarrierClient;
import com.ordersphere.shipping.carrier.CarrierUpdate;
import com.ordersphere.shipping.domain.Shipment;
import com.ordersphere.shipping.domain.ShipmentStatus;
import com.ordersphere.shipping.domain.ShipmentTrackingEvent;
import com.ordersphere.shipping.domain.ShipmentType;
import com.ordersphere.shipping.dto.CreateShipmentRequest;
import com.ordersphere.shipping.dto.ReturnShipmentRequest;
import com.ordersphere.shipping.dto.ShipmentResponse;
import com.ordersphere.shipping.dto.TrackingEventResponse;
import com.ordersphere.shipping.exception.InvalidShipmentStateException;
import com.ordersphere.shipping.exception.ShipmentNotFoundException;
import com.ordersphere.shipping.repository.ShipmentRepository;
import com.ordersphere.shipping.repository.ShipmentTrackingEventRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {

  private final ShipmentRepository shipmentRepository;
  private final ShipmentTrackingEventRepository trackingEventRepository;
  private final CarrierClient carrierClient;
  private final ApplicationEventPublisher eventPublisher;

  public ShipmentService(
      ShipmentRepository shipmentRepository,
      ShipmentTrackingEventRepository trackingEventRepository,
      CarrierClient carrierClient,
      ApplicationEventPublisher eventPublisher) {
    this.shipmentRepository = shipmentRepository;
    this.trackingEventRepository = trackingEventRepository;
    this.carrierClient = carrierClient;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public ShipmentResponse createShipment(CreateShipmentRequest request) {
    Optional<Shipment> existing =
        shipmentRepository.findByOrderIdAndType(request.orderId(), ShipmentType.OUTBOUND);
    if (existing.isPresent()) {
      return ShipmentResponse.from(existing.get());
    }

    Shipment shipment =
        new Shipment(request.orderId(), ShipmentType.OUTBOUND, request.destination(), null);
    createWithInitialTracking(shipment);

    return ShipmentResponse.from(shipment);
  }

  @Transactional(readOnly = true)
  public ShipmentResponse getShipment(Long id) {
    return ShipmentResponse.from(findShipmentOrThrow(id));
  }

  @Transactional(readOnly = true)
  public List<ShipmentResponse> listByOrder(Long orderId) {
    return shipmentRepository.findByOrderId(orderId).stream().map(ShipmentResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public List<TrackingEventResponse> getTracking(Long shipmentId) {
    findShipmentOrThrow(shipmentId);
    return trackingEventRepository.findByShipmentIdOrderByOccurredAtAsc(shipmentId).stream()
        .map(TrackingEventResponse::from)
        .toList();
  }

  @Transactional
  public ShipmentResponse requestReturn(Long shipmentId, ReturnShipmentRequest request) {
    Shipment original = findShipmentOrThrow(shipmentId);

    if (original.getType() != ShipmentType.OUTBOUND) {
      throw new InvalidShipmentStateException("Only outbound shipments can be returned");
    }
    if (original.getStatus() != ShipmentStatus.DELIVERED) {
      throw new InvalidShipmentStateException(
          "Cannot return shipment " + shipmentId + " before it is delivered");
    }

    Optional<Shipment> existingReturn =
        shipmentRepository.findByOrderIdAndType(original.getOrderId(), ShipmentType.RETURN);
    if (existingReturn.isPresent()) {
      return ShipmentResponse.from(existingReturn.get());
    }

    Shipment returnShipment =
        new Shipment(
            original.getOrderId(), ShipmentType.RETURN, original.getDestination(), shipmentId);
    createWithInitialTracking(returnShipment);

    return ShipmentResponse.from(returnShipment);
  }

  @Transactional
  public void advance(Shipment shipment) {
    if (shipment.getStatus() == ShipmentStatus.DELIVERED) {
      return;
    }

    CarrierUpdate update = carrierClient.nextStage(shipment.getStatus());
    shipment.advanceTo(update.nextStatus());
    shipmentRepository.save(shipment);
    trackingEventRepository.save(
        new ShipmentTrackingEvent(shipment, update.nextStatus(), update.location()));

    switch (update.nextStatus()) {
      case PICKED ->
          eventPublisher.publishEvent(
              new ShipmentPickedEvent(shipment.getId(), shipment.getOrderId()));
      case IN_TRANSIT ->
          eventPublisher.publishEvent(
              new ShipmentInTransitEvent(shipment.getId(), shipment.getOrderId()));
      case DELIVERED ->
          eventPublisher.publishEvent(
              new DeliveryConfirmedEvent(
                  shipment.getId(), shipment.getOrderId(), shipment.getDeliveredAt()));
      case CREATED ->
          throw new IllegalStateException("Carrier cannot advance a shipment to CREATED");
    }
  }

  private void createWithInitialTracking(Shipment shipment) {
    shipmentRepository.save(shipment);
    trackingEventRepository.save(
        new ShipmentTrackingEvent(shipment, ShipmentStatus.CREATED, "Origin facility"));
    eventPublisher.publishEvent(
        new ShipmentCreatedEvent(
            shipment.getId(), shipment.getOrderId(), shipment.getDestination()));
  }

  private Shipment findShipmentOrThrow(Long id) {
    return shipmentRepository.findById(id).orElseThrow(() -> new ShipmentNotFoundException(id));
  }
}
