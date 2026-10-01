package com.ordersphere.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.events.InventoryReleasedEvent;
import com.ordersphere.events.InventoryReservedEvent;
import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.domain.Reservation;
import com.ordersphere.inventory.domain.ReservationItem;
import com.ordersphere.inventory.domain.ReservationStatus;
import com.ordersphere.inventory.dto.ReservationResponse;
import com.ordersphere.inventory.dto.ReserveStockRequest;
import com.ordersphere.inventory.exception.InsufficientStockException;
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
  @Mock private ApplicationEventPublisher eventPublisher;

  private ReservationService reservationService;

  @BeforeEach
  void setUp() {
    reservationService =
        new ReservationService(reservationRepository, productRepository, eventPublisher, 15L);
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
    assertThat(product.getQuantityReserved()).isEqualTo(5);
    verify(eventPublisher).publishEvent(any(InventoryReservedEvent.class));
  }

  @Test
  void rejectsAnOrderForMoreThanIsAvailableWithoutReservingAnything() {
    Product product = new Product("SKU-1", "Widget", 2, 0, new BigDecimal("9.99"));
    when(reservationRepository.findByOrderId(3L)).thenReturn(Optional.empty());
    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));

    assertThatThrownBy(
            () ->
                reservationService.reserve(
                    new ReserveStockRequest(3L, List.of(new ReserveStockRequest.Item("SKU-1", 5)))))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessage("Not enough stock: SKU-1 (5 requested, 2 available)");

    assertThat(product.getQuantityReserved()).isZero();
    verify(reservationRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void rejectsAnOrderForAProductWithNoStock() {
    Product product = new Product("SKU-1", "Widget", 0, 0, new BigDecimal("9.99"));
    when(reservationRepository.findByOrderId(4L)).thenReturn(Optional.empty());
    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));

    assertThatThrownBy(
            () ->
                reservationService.reserve(
                    new ReserveStockRequest(4L, List.of(new ReserveStockRequest.Item("SKU-1", 1)))))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessage("Not enough stock: SKU-1 (1 requested, 0 available)");
  }

  @Test
  void oneShortLineMeansNothingIsReservedForAnyLine() {
    Product plenty = new Product("SKU-A", "Plenty", 50, 0, new BigDecimal("1.00"));
    Product scarce = new Product("SKU-B", "Scarce", 1, 0, new BigDecimal("2.00"));
    when(reservationRepository.findByOrderId(7L)).thenReturn(Optional.empty());
    when(productRepository.findWithLockBySku("SKU-A")).thenReturn(Optional.of(plenty));
    when(productRepository.findWithLockBySku("SKU-B")).thenReturn(Optional.of(scarce));

    assertThatThrownBy(
            () ->
                reservationService.reserve(
                    new ReserveStockRequest(
                        7L,
                        List.of(
                            new ReserveStockRequest.Item("SKU-A", 5),
                            new ReserveStockRequest.Item("SKU-B", 3)))))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessage("Not enough stock: SKU-B (3 requested, 1 available)");

    assertThat(plenty.getQuantityReserved()).isZero();
    assertThat(scarce.getQuantityReserved()).isZero();
  }

  @Test
  void reservesExactlyWhatIsAvailable() {
    Product product = new Product("SKU-1", "Widget", 3, 0, new BigDecimal("9.99"));
    when(reservationRepository.findByOrderId(8L)).thenReturn(Optional.empty());
    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));

    ReservationResponse response =
        reservationService.reserve(
            new ReserveStockRequest(8L, List.of(new ReserveStockRequest.Item("SKU-1", 3))));

    assertThat(response.reserved())
        .containsExactly(new ReservationResponse.LineItem("SKU-1", 3, new BigDecimal("9.99")));
    assertThat(product.getAvailableQuantity()).isZero();
  }

  @Test
  void repeatedLinesForTheSameSkuAreCheckedTogether() {
    Product product = new Product("SKU-1", "Widget", 4, 0, new BigDecimal("9.99"));
    when(reservationRepository.findByOrderId(9L)).thenReturn(Optional.empty());
    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));

    // 3 + 3 = 6 > 4, even though each line on its own would fit.
    assertThatThrownBy(
            () ->
                reservationService.reserve(
                    new ReserveStockRequest(
                        9L,
                        List.of(
                            new ReserveStockRequest.Item("SKU-1", 3),
                            new ReserveStockRequest.Item("SKU-1", 3)))))
        .isInstanceOf(InsufficientStockException.class)
        .hasMessage("Not enough stock: SKU-1 (6 requested, 4 available)");
    assertThat(product.getQuantityReserved()).isZero();
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
