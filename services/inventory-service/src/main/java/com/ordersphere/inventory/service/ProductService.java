package com.ordersphere.inventory.service;

import com.ordersphere.events.InventoryReservedEvent;
import com.ordersphere.inventory.domain.Backorder;
import com.ordersphere.inventory.domain.BackorderStatus;
import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.domain.Reservation;
import com.ordersphere.inventory.domain.ReservationItem;
import com.ordersphere.inventory.dto.CreateProductRequest;
import com.ordersphere.inventory.dto.ProductResponse;
import com.ordersphere.inventory.exception.DuplicateSkuException;
import com.ordersphere.inventory.exception.ProductNotFoundException;
import com.ordersphere.inventory.repository.BackorderRepository;
import com.ordersphere.inventory.repository.ProductRepository;
import com.ordersphere.inventory.repository.ReservationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

  private final ProductRepository productRepository;
  private final BackorderRepository backorderRepository;
  private final ReservationRepository reservationRepository;
  private final ApplicationEventPublisher eventPublisher;

  public ProductService(
      ProductRepository productRepository,
      BackorderRepository backorderRepository,
      ReservationRepository reservationRepository,
      ApplicationEventPublisher eventPublisher) {
    this.productRepository = productRepository;
    this.backorderRepository = backorderRepository;
    this.reservationRepository = reservationRepository;
    this.eventPublisher = eventPublisher;
  }

  public ProductResponse createProduct(CreateProductRequest request) {
    if (productRepository.existsBySku(request.sku())) {
      throw new DuplicateSkuException(request.sku());
    }
    Product product =
        new Product(
            request.sku(),
            request.name(),
            request.quantityOnHand(),
            request.reorderThreshold(),
            request.unitPrice());
    return ProductResponse.from(productRepository.save(product));
  }

  @Transactional(readOnly = true)
  public List<ProductResponse> listProducts() {
    return productRepository.findAll().stream().map(ProductResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public ProductResponse getProduct(String sku) {
    return ProductResponse.from(findProductOrThrow(sku));
  }

  @Transactional
  public ProductResponse restock(String sku, int quantity) {
    Product product =
        productRepository
            .findWithLockBySku(sku)
            .orElseThrow(() -> new ProductNotFoundException(sku));
    product.setQuantityOnHand(product.getQuantityOnHand() + quantity);
    productRepository.save(product);

    fulfillOpenBackorders(product);

    return ProductResponse.from(product);
  }

  private void fulfillOpenBackorders(Product product) {
    List<Backorder> openBackorders =
        backorderRepository.findByProductIdAndStatusOrderByCreatedAtAsc(
            product.getId(), BackorderStatus.OPEN);

    for (Backorder backorder : openBackorders) {
      int available = product.getAvailableQuantity();
      if (available <= 0) {
        break;
      }

      int fulfillQuantity = Math.min(available, backorder.getQuantity());
      applyFulfillment(product, backorder, fulfillQuantity);
    }
  }

  private void applyFulfillment(Product product, Backorder backorder, int fulfillQuantity) {
    Reservation reservation =
        reservationRepository
            .findByOrderId(backorder.getOrderId())
            .orElseThrow(() -> new IllegalStateException("Backorder has no matching reservation"));

    ReservationItem existingItem =
        reservation.getItems().stream()
            .filter(item -> item.getProduct().getId().equals(product.getId()))
            .findFirst()
            .orElse(null);

    if (existingItem != null) {
      existingItem.setQuantityReserved(existingItem.getQuantityReserved() + fulfillQuantity);
    } else {
      reservation.addItem(new ReservationItem(product, fulfillQuantity));
    }

    product.setQuantityReserved(product.getQuantityReserved() + fulfillQuantity);

    if (fulfillQuantity >= backorder.getQuantity()) {
      backorder.setStatus(BackorderStatus.FULFILLED);
      backorder.setFulfilledAt(Instant.now());
    } else {
      backorder.setQuantity(backorder.getQuantity() - fulfillQuantity);
    }

    reservationRepository.save(reservation);
    backorderRepository.save(backorder);

    eventPublisher.publishEvent(
        new InventoryReservedEvent(
            backorder.getOrderId(), Map.of(product.getSku(), fulfillQuantity)));
  }

  private Product findProductOrThrow(String sku) {
    return productRepository.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
  }
}
