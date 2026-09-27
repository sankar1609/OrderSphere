package com.ordersphere.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.events.BackorderCreatedEvent;
import com.ordersphere.events.InventoryReleasedEvent;
import com.ordersphere.events.InventoryReservedEvent;
import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.domain.Reservation;
import com.ordersphere.inventory.domain.ReservationItem;
import com.ordersphere.inventory.domain.ReservationStatus;
import com.ordersphere.inventory.dto.ReservationResponse;
import com.ordersphere.inventory.dto.ReserveStockRequest;
import com.ordersphere.inventory.repository.BackorderRepository;
import com.ordersphere.inventory.repository.ProductRepository;
import com.ordersphere.inventory.repository.ReservationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

  @Mock private ReservationRepository reservationRepository;
  @Mock private ProductRepository productRepository;
  @Mock private BackorderRepository backorderRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  private ReservationService reservationService;

  @BeforeEach
  void setUp() {
    reservationService =
        new ReservationService(
            reservationRepository, productRepository, backorderRepository, eventPublisher, 15L);
  }

  @Test
  void reservationIsIdempotentForSameOrderId() {
    Reservation existing = new Reservation(1L, null);
    when(reservationRepository.findByOrderId(1L)).thenReturn(Optional.of(existing));

    ReservationResponse response =
        reservationService.reserve(
            new ReserveStockRequest(1L, List.of(new ReserveStockRequest.Item("SKU-1", 5))));

    assertThat(response.orderId()).isEqualTo(1L);
    verify(productRepository, never()).findWithLockBySku(any());
  }

  @Test
  void fullyReservesWhenStockSufficient() {
    Product product = new Product("SKU-1", "Widget", 10, 3, new BigDecimal("9.99"));
    when(reservationRepository.findByOrderId(2L)).thenReturn(Optional.empty());
    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));

    ReservationResponse response =
        reservationService.reserve(
            new ReserveStockRequest(2L, List.of(new ReserveStockRequest.Item("SKU-1", 5))));

    assertThat(response.reserved())
        .containsExactly(new ReservationResponse.LineItem("SKU-1", 5, new BigDecimal("9.99")));
    assertThat(response.backordered()).isEmpty();
    assertThat(product.getQuantityReserved()).isEqualTo(5);
    verify(eventPublisher).publishEvent(any(InventoryReservedEvent.class));
    verify(eventPublisher, never()).publishEvent(any(BackorderCreatedEvent.class));
  }

  @Test
  void partiallyReservesAndBackordersShortfall() {
    Product product = new Product("SKU-1", "Widget", 2, 0, new BigDecimal("9.99"));
    when(reservationRepository.findByOrderId(3L)).thenReturn(Optional.empty());
    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));

    ReservationResponse response =
        reservationService.reserve(
            new ReserveStockRequest(3L, List.of(new ReserveStockRequest.Item("SKU-1", 5))));

    assertThat(response.reserved())
        .containsExactly(new ReservationResponse.LineItem("SKU-1", 2, new BigDecimal("9.99")));
    assertThat(response.backordered())
        .containsExactly(new ReservationResponse.LineItem("SKU-1", 3, new BigDecimal("9.99")));
    verify(eventPublisher).publishEvent(any(InventoryReservedEvent.class));
    verify(eventPublisher).publishEvent(any(BackorderCreatedEvent.class));
  }

  @Test
  void createsBackorderOnlyWhenNoStockAvailable() {
    Product product = new Product("SKU-1", "Widget", 0, 0, new BigDecimal("9.99"));
    when(reservationRepository.findByOrderId(4L)).thenReturn(Optional.empty());
    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));

    ReservationResponse response =
        reservationService.reserve(
            new ReserveStockRequest(4L, List.of(new ReserveStockRequest.Item("SKU-1", 5))));

    assertThat(response.reserved()).isEmpty();
    assertThat(response.backordered())
        .containsExactly(new ReservationResponse.LineItem("SKU-1", 5, new BigDecimal("9.99")));
    verify(eventPublisher, never()).publishEvent(any(InventoryReservedEvent.class));
    verify(eventPublisher).publishEvent(any(BackorderCreatedEvent.class));
  }

  @Test
  void confirmClearsExpiryAndIsIdempotent() {
    Reservation reservation = new Reservation(5L, Instant.now().plusSeconds(60));
    when(reservationRepository.findByOrderId(5L)).thenReturn(Optional.of(reservation));

    reservationService.confirm(5L);

    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    assertThat(reservation.getExpiresAt()).isNull();

    ReservationResponse second = reservationService.confirm(5L);
    assertThat(second.status()).isEqualTo(ReservationStatus.CONFIRMED);
  }

  @Test
  void releaseRestoresStockAndIsIdempotent() {
    Product product = new Product("SKU-1", "Widget", 10, 0, new BigDecimal("9.99"));
    product.setQuantityReserved(4);
    Reservation reservation = new Reservation(6L, Instant.now().plusSeconds(60));
    reservation.addItem(new ReservationItem(product, 4));
    when(reservationRepository.findByOrderId(6L)).thenReturn(Optional.of(reservation));
    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));

    reservationService.release(6L);

    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    assertThat(product.getQuantityReserved()).isZero();
    verify(eventPublisher).publishEvent(any(InventoryReleasedEvent.class));

    ReservationResponse second = reservationService.release(6L);
    assertThat(second.status()).isEqualTo(ReservationStatus.RELEASED);
    assertThat(product.getQuantityReserved()).isZero();
  }
}
