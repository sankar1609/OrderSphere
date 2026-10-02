package com.ordersphere.inventory.service;

import com.ordersphere.events.InventoryReleasedEvent;
import com.ordersphere.events.InventoryReservedEvent;
import com.ordersphere.events.StockLowEvent;
import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.domain.Reservation;
import com.ordersphere.inventory.domain.ReservationItem;
import com.ordersphere.inventory.domain.ReservationStatus;
import com.ordersphere.inventory.dto.ReservationResponse;
import com.ordersphere.inventory.dto.ReserveStockRequest;
import com.ordersphere.inventory.exception.InsufficientStockException;
import com.ordersphere.inventory.exception.InvalidReservationStateException;
import com.ordersphere.inventory.exception.ProductNotFoundException;
import com.ordersphere.inventory.exception.ReservationNotFoundException;
import com.ordersphere.inventory.repository.ProductRepository;
import com.ordersphere.inventory.repository.ReservationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

  private final ReservationRepository reservationRepository;
  private final ProductRepository productRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final long holdTtlMinutes;

  public ReservationService(
      ReservationRepository reservationRepository,
      ProductRepository productRepository,
      ApplicationEventPublisher eventPublisher,
      @Value("${inventory.reservation.hold-ttl-minutes}") long holdTtlMinutes) {
    this.reservationRepository = reservationRepository;
    this.productRepository = productRepository;
    this.eventPublisher = eventPublisher;
    this.holdTtlMinutes = holdTtlMinutes;
  }

  @Transactional
  public ReservationResponse reserve(ReserveStockRequest request) {
    var existing = reservationRepository.findByOrderId(request.orderId());
    if (existing.isPresent()) {
      return ReservationResponse.from(existing.get());
    }

    // All or nothing: lock every product first (in SKU order, so two orders for overlapping
    // products can't deadlock), and only reserve once each one has enough available.
    Map<String, Integer> requested = new TreeMap<>();
    for (ReserveStockRequest.Item item : request.items()) {
      // addExact: request validation caps quantities, but a sum that wrapped negative would
      // reserve negative stock - creating it out of nothing - so fail loudly instead.
      requested.merge(item.sku(), item.quantity(), Math::addExact);
    }
    Map<String, Product> products = new LinkedHashMap<>();
    List<InsufficientStockException.Shortage> shortages = new ArrayList<>();
    for (Map.Entry<String, Integer> line : requested.entrySet()) {
      Product product =
          productRepository
              .findWithLockBySku(line.getKey())
              .orElseThrow(() -> new ProductNotFoundException(line.getKey()));
      products.put(line.getKey(), product);
      if (product.getAvailableQuantity() < line.getValue()) {
        shortages.add(
            new InsufficientStockException.Shortage(
                line.getKey(), line.getValue(), product.getAvailableQuantity()));
      }
    }
    if (!shortages.isEmpty()) {
      throw new InsufficientStockException(shortages);
    }

    Reservation reservation =
        new Reservation(request.orderId(), Instant.now().plus(holdTtlMinutes, ChronoUnit.MINUTES));
    for (Map.Entry<String, Integer> line : requested.entrySet()) {
      Product product = products.get(line.getKey());
      int availableBefore = product.getAvailableQuantity();
      reservation.addItem(new ReservationItem(product, line.getValue()));
      product.setQuantityReserved(product.getQuantityReserved() + line.getValue());
      productRepository.save(product);

      // Alert once, as stock crosses the threshold - not on every order while it stays low.
      // Releases, expiries and restocks raise it back above, which re-arms the alert.
      if (availableBefore > product.getReorderThreshold()
          && product.getAvailableQuantity() <= product.getReorderThreshold()) {
        eventPublisher.publishEvent(
            new StockLowEvent(
                product.getSku(),
                product.getName(),
                product.getAvailableQuantity(),
                product.getReorderThreshold(),
                product.getCreatedBy()));
      }
    }
    reservationRepository.save(reservation);

    eventPublisher.publishEvent(new InventoryReservedEvent(request.orderId(), requested));

    return ReservationResponse.from(reservation);
  }

  @Transactional
  public ReservationResponse confirm(Long orderId) {
    Reservation reservation = findReservationOrThrow(orderId);

    if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
      return ReservationResponse.from(reservation);
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

    return ReservationResponse.from(reservation);
  }

  @Transactional
  public ReservationResponse release(Long orderId) {
    Reservation reservation = findReservationOrThrow(orderId);

    if (reservation.getStatus() == ReservationStatus.RELEASED
        || reservation.getStatus() == ReservationStatus.EXPIRED) {
      return ReservationResponse.from(reservation);
    }

    restoreStock(reservation);
    reservation.setStatus(ReservationStatus.RELEASED);
    reservation.setExpiresAt(null);
    reservationRepository.save(reservation);

    eventPublisher.publishEvent(
        new InventoryReleasedEvent(orderId, InventoryReleasedEvent.Reason.MANUAL));

    return ReservationResponse.from(reservation);
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

  private Reservation findReservationOrThrow(Long orderId) {
    return reservationRepository
        .findByOrderId(orderId)
        .orElseThrow(() -> new ReservationNotFoundException(orderId));
  }
}
