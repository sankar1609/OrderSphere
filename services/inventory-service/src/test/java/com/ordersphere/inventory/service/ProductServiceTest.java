package com.ordersphere.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.events.InventoryReservedEvent;
import com.ordersphere.inventory.domain.Backorder;
import com.ordersphere.inventory.domain.BackorderStatus;
import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.domain.Reservation;
import com.ordersphere.inventory.dto.CreateProductRequest;
import com.ordersphere.inventory.dto.ProductResponse;
import com.ordersphere.inventory.exception.DuplicateSkuException;
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
class ProductServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private BackorderRepository backorderRepository;
  @Mock private ReservationRepository reservationRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  private ProductService productService;

  @BeforeEach
  void setUp() {
    productService =
        new ProductService(
            productRepository, backorderRepository, reservationRepository, eventPublisher);
  }

  @Test
  void createProductRejectsDuplicateSku() {
    when(productRepository.existsBySku("SKU-1")).thenReturn(true);

    assertThatThrownBy(
            () ->
                productService.createProduct(
                    new CreateProductRequest("SKU-1", "Widget", 5, 1, new BigDecimal("9.99"))))
        .isInstanceOf(DuplicateSkuException.class);
  }

  @Test
  void restockFullyFulfillsAnOpenBackorder() {
    Product product = new Product("SKU-1", "Widget", 0, 0, new BigDecimal("9.99"));
    product.setId(1L);
    Backorder backorder = new Backorder(product, 42L, 4);
    Reservation reservation = new Reservation(42L, Instant.now().plusSeconds(60));

    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));
    when(backorderRepository.findByProductIdAndStatusOrderByCreatedAtAsc(1L, BackorderStatus.OPEN))
        .thenReturn(List.of(backorder));
    when(reservationRepository.findByOrderId(42L)).thenReturn(Optional.of(reservation));

    ProductResponse response = productService.restock("SKU-1", 10);

    assertThat(response.quantityOnHand()).isEqualTo(10);
    assertThat(backorder.getStatus()).isEqualTo(BackorderStatus.FULFILLED);
    assertThat(reservation.getItems()).hasSize(1);
    assertThat(reservation.getItems().get(0).getQuantityReserved()).isEqualTo(4);
    assertThat(product.getQuantityReserved()).isEqualTo(4);
    verify(eventPublisher).publishEvent(any(InventoryReservedEvent.class));
  }

  @Test
  void restockPartiallyFulfillsBackorderWhenStockStillInsufficient() {
    Product product = new Product("SKU-1", "Widget", 0, 0, new BigDecimal("9.99"));
    product.setId(1L);
    Backorder backorder = new Backorder(product, 42L, 10);
    Reservation reservation = new Reservation(42L, Instant.now().plusSeconds(60));

    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));
    when(backorderRepository.findByProductIdAndStatusOrderByCreatedAtAsc(1L, BackorderStatus.OPEN))
        .thenReturn(List.of(backorder));
    when(reservationRepository.findByOrderId(42L)).thenReturn(Optional.of(reservation));

    productService.restock("SKU-1", 4);

    assertThat(backorder.getStatus()).isEqualTo(BackorderStatus.OPEN);
    assertThat(backorder.getQuantity()).isEqualTo(6);
    assertThat(product.getQuantityReserved()).isEqualTo(4);
  }
}
