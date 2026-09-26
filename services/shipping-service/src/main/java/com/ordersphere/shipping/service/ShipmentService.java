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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShipmentService {

  private final ShipmentRepository shipmentRepository;
  private final ShipmentTrackingEventRepository trackingEventRepository;
  private final CarrierClient carrierClient;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Self-reference injected through Spring's proxy, needed so insertShipment/findExistingShipment
   * run in their own transactions even when called from createShipment/requestReturn on this same
   * bean (a plain `this.insertShipment(...)` call would bypass the proxy and its @Transactional
   * advice entirely). Package-private so tests can set it directly instead of via reflection.
   */
  @Autowired @Lazy ShipmentService self;

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

  /**
   * The findByOrderIdAndType check below can't be made atomic with the insert via a row lock -
   * there's no row to lock until the first caller creates one. Two concurrent calls for the same
   * order can both pass the check and both attempt the insert; the DB's uq_shipments_order_id_type
   * constraint is what actually serializes them, so the loser's insert fails here and we fall back
   * to fetching the winner's row instead of surfacing a 500 (which is what used to happen, silently
   * dropping the order's shipmentId on the orders-service side).
   */
  public ShipmentResponse createShipment(CreateShipmentRequest request) {
    try {
      return self.insertShipment(
          request.orderId(),
          request.customerUsername(),
          ShipmentType.OUTBOUND,
          request.destination(),
          null);
    } catch (DataIntegrityViolationException ex) {
      return self.findExistingShipment(request.orderId(), ShipmentType.OUTBOUND);
    }
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

  public ShipmentResponse requestReturn(Long shipmentId, ReturnShipmentRequest request) {
    Shipment original = findShipmentOrThrow(shipmentId);

    if (original.getType() != ShipmentType.OUTBOUND) {
      throw new InvalidShipmentStateException("Only outbound shipments can be returned");
    }
    if (original.getStatus() != ShipmentStatus.DELIVERED) {
      throw new InvalidShipmentStateException(
          "Cannot return shipment " + shipmentId + " before it is delivered");
    }

    try {
      return self.insertShipment(
          original.getOrderId(),
          original.getCustomerUsername(),
          ShipmentType.RETURN,
          original.getDestination(),
          shipmentId);
    } catch (DataIntegrityViolationException ex) {
      return self.findExistingShipment(original.getOrderId(), ShipmentType.RETURN);
    }
  }

  @Transactional
  public void cancelForOrder(Long orderId) {
    shipmentRepository
        .findByOrderIdAndType(orderId, ShipmentType.OUTBOUND)
        .filter(
            shipment ->
                shipment.getStatus() != ShipmentStatus.DELIVERED
                    && shipment.getStatus() != ShipmentStatus.CANCELLED)
        .ifPresent(
            shipment -> {
              shipment.advanceTo(ShipmentStatus.CANCELLED);
              shipmentRepository.save(shipment);
              trackingEventRepository.save(
                  new ShipmentTrackingEvent(shipment, ShipmentStatus.CANCELLED, "Order cancelled"));
            });
  }

  @Transactional
  public void advance(Shipment shipment) {
    if (shipment.getStatus() == ShipmentStatus.DELIVERED
        || shipment.getStatus() == ShipmentStatus.CANCELLED) {
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
              new ShipmentPickedEvent(
                  shipment.getId(), shipment.getOrderId(), shipment.getCustomerUsername()));
      case IN_TRANSIT ->
          eventPublisher.publishEvent(
              new ShipmentInTransitEvent(
                  shipment.getId(), shipment.getOrderId(), shipment.getCustomerUsername()));
      case DELIVERED ->
          eventPublisher.publishEvent(
              new DeliveryConfirmedEvent(
                  shipment.getId(),
                  shipment.getOrderId(),
                  shipment.getCustomerUsername(),
                  shipment.getDeliveredAt()));
      case CREATED ->
          throw new IllegalStateException("Carrier cannot advance a shipment to CREATED");
    }
  }

  @Transactional
  protected ShipmentResponse insertShipment(
      Long orderId,
      String customerUsername,
      ShipmentType type,
      String destination,
      Long parentShipmentId) {
    Optional<Shipment> existing = shipmentRepository.findByOrderIdAndType(orderId, type);
    if (existing.isPresent()) {
      return ShipmentResponse.from(existing.get());
    }

    Shipment shipment =
        new Shipment(orderId, customerUsername, type, destination, parentShipmentId);
    createWithInitialTracking(shipment);

    return ShipmentResponse.from(shipment);
  }

  @Transactional(readOnly = true)
  protected ShipmentResponse findExistingShipment(Long orderId, ShipmentType type) {
    return shipmentRepository
        .findByOrderIdAndType(orderId, type)
        .map(ShipmentResponse::from)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Expected an existing "
                        + type
                        + " shipment for orderId "
                        + orderId
                        + " after a unique-constraint conflict, but found none"));
  }

  private void createWithInitialTracking(Shipment shipment) {
    shipmentRepository.save(shipment);
    trackingEventRepository.save(
        new ShipmentTrackingEvent(shipment, ShipmentStatus.CREATED, "Origin facility"));
    eventPublisher.publishEvent(
        new ShipmentCreatedEvent(
            shipment.getId(),
            shipment.getOrderId(),
            shipment.getCustomerUsername(),
            shipment.getDestination()));
  }

  private Shipment findShipmentOrThrow(Long id) {
    return shipmentRepository.findById(id).orElseThrow(() -> new ShipmentNotFoundException(id));
  }
}
