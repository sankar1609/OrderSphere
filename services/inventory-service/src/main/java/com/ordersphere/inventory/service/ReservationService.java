package com.ordersphere.inventory.service;

import com.ordersphere.events.BackorderCreatedEvent;
import com.ordersphere.events.InventoryReleasedEvent;
import com.ordersphere.events.InventoryReservedEvent;
import com.ordersphere.events.StockLowEvent;
import com.ordersphere.inventory.domain.Backorder;
import com.ordersphere.inventory.domain.BackorderStatus;
import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.domain.Reservation;
import com.ordersphere.inventory.domain.ReservationItem;
import com.ordersphere.inventory.domain.ReservationStatus;
import com.ordersphere.inventory.dto.ReservationResponse;
import com.ordersphere.inventory.dto.ReserveStockRequest;
import com.ordersphere.inventory.exception.InvalidReservationStateException;
import com.ordersphere.inventory.exception.ProductNotFoundException;
import com.ordersphere.inventory.exception.ReservationNotFoundException;
import com.ordersphere.inventory.repository.BackorderRepository;
import com.ordersphere.inventory.repository.ProductRepository;
import com.ordersphere.inventory.repository.ReservationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

  private final ReservationRepository reservationRepository;
  private final ProductRepository productRepository;
  private final BackorderRepository backorderRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final long holdTtlMinutes;

  public ReservationService(
      ReservationRepository reservationRepository,
      ProductRepository productRepository,
      BackorderRepository backorderRepository,
      ApplicationEventPublisher eventPublisher,
      @Value("${inventory.reservation.hold-ttl-minutes}") long holdTtlMinutes) {
    this.reservationRepository = reservationRepository;
    this.productRepository = productRepository;
    this.backorderRepository = backorderRepository;
    this.eventPublisher = eventPublisher;
    this.holdTtlMinutes = holdTtlMinutes;
  }

  @Transactional
  public ReservationResponse reserve(ReserveStockRequest request) {
    var existing = reservationRepository.findByOrderId(request.orderId());
    if (existing.isPresent()) {
      return buildResponseForExisting(existing.get());
    }

    Reservation reservation =
        new Reservation(request.orderId(), Instant.now().plus(holdTtlMinutes, ChronoUnit.MINUTES));

    Map<String, Integer> reservedQuantities = new HashMap<>();
    Map<String, Integer> backorderedQuantities = new HashMap<>();

    for (ReserveStockRequest.Item item : request.items()) {
      Product product =
          productRepository
              .findWithLockBySku(item.sku())
              .orElseThrow(() -> new ProductNotFoundException(item.sku()));

      int available = product.getAvailableQuantity();
      int reserveQuantity = Math.min(available, item.quantity());
      int shortfall = item.quantity() - reserveQuantity;

      if (reserveQuantity > 0) {
        reservation.addItem(new ReservationItem(product, reserveQuantity));
        product.setQuantityReserved(product.getQuantityReserved() + reserveQuantity);
        productRepository.save(product);
        reservedQuantities.merge(item.sku(), reserveQuantity, Integer::sum);

        if (product.getAvailableQuantity() <= product.getReorderThreshold()) {
          eventPublisher.publishEvent(
              new StockLowEvent(
                  product.getSku(), product.getAvailableQuantity(), product.getReorderThreshold()));
        }
      }

      if (shortfall > 0) {
        backorderRepository.save(new Backorder(product, request.orderId(), shortfall));
        backorderedQuantities.merge(item.sku(), shortfall, Integer::sum);
        eventPublisher.publishEvent(
            new BackorderCreatedEvent(request.orderId(), item.sku(), shortfall));
      }
    }

    reservationRepository.save(reservation);

    if (!reservedQuantities.isEmpty()) {
      eventPublisher.publishEvent(
          new InventoryReservedEvent(request.orderId(), reservedQuantities));
    }

    return ReservationResponse.of(reservation, backorderedQuantities);
  }

  @Transactional
  public ReservationResponse confirm(Long orderId) {
    Reservation reservation = findReservationOrThrow(orderId);

    if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
      return ReservationResponse.of(reservation, Map.of());
    }
    if (reservation.getStatus() != ReservationStatus.ACTIVE) {
      throw new InvalidReservationStateException(
          "Cannot confirm reservation for orderId "
              + orderId
              + " in status "
              + reservation.getStatus());
    }

    reservation.setStatus(ReservationStatus.CONFIRMED);
    reservation.setExpiresAt(null);
    reservationRepository.save(reservation);

    return ReservationResponse.of(reservation, Map.of());
  }

  @Transactional
  public ReservationResponse release(Long orderId) {
    Reservation reservation = findReservationOrThrow(orderId);

    if (reservation.getStatus() == ReservationStatus.RELEASED
        || reservation.getStatus() == ReservationStatus.EXPIRED) {
      return ReservationResponse.of(reservation, Map.of());
    }

    restoreStock(reservation);
    reservation.setStatus(ReservationStatus.RELEASED);
    reservation.setExpiresAt(null);
    reservationRepository.save(reservation);

    eventPublisher.publishEvent(
        new InventoryReleasedEvent(orderId, InventoryReleasedEvent.Reason.MANUAL));

    return ReservationResponse.of(reservation, Map.of());
  }

  @Transactional
  public void expire(Reservation reservation) {
    restoreStock(reservation);
    reservation.setStatus(ReservationStatus.EXPIRED);
    reservation.setExpiresAt(null);
    reservationRepository.save(reservation);

    eventPublisher.publishEvent(
        new InventoryReleasedEvent(
            reservation.getOrderId(), InventoryReleasedEvent.Reason.EXPIRED));
  }

  private void restoreStock(Reservation reservation) {
    for (ReservationItem item : reservation.getItems()) {
      Product product =
          productRepository
              .findWithLockBySku(item.getProduct().getSku())
              .orElseThrow(() -> new ProductNotFoundException(item.getProduct().getSku()));
      product.setQuantityReserved(product.getQuantityReserved() - item.getQuantityReserved());
      productRepository.save(product);
    }
  }

  private ReservationResponse buildResponseForExisting(Reservation reservation) {
    List<Backorder> openBackorders =
        backorderRepository.findByOrderIdAndStatus(reservation.getOrderId(), BackorderStatus.OPEN);
    Map<String, Integer> backorderedQuantities = new HashMap<>();
    for (Backorder backorder : openBackorders) {
      backorderedQuantities.merge(
          backorder.getProduct().getSku(), backorder.getQuantity(), Integer::sum);
    }
    return ReservationResponse.of(reservation, backorderedQuantities);
  }

  private Reservation findReservationOrThrow(Long orderId) {
    return reservationRepository
        .findByOrderId(orderId)
        .orElseThrow(() -> new ReservationNotFoundException(orderId));
  }
}
