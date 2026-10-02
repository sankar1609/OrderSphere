package com.ordersphere.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.dto.CreateProductRequest;
import com.ordersphere.inventory.dto.InventoryLimits;
import com.ordersphere.inventory.dto.ProductResponse;
import com.ordersphere.inventory.exception.DuplicateSkuException;
import com.ordersphere.inventory.exception.ProductOwnershipException;
import com.ordersphere.inventory.exception.StockLimitExceededException;
import com.ordersphere.inventory.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;

  private ProductService productService;

  @BeforeEach
  void setUp() {
    productService = new ProductService(productRepository);
  }

  @Test
  void createProductRejectsDuplicateSku() {
    when(productRepository.existsBySku("SKU-1")).thenReturn(true);

    assertThatThrownBy(
            () ->
                productService.createProduct(
                    new CreateProductRequest("SKU-1", "Widget", 5, 1, new BigDecimal("9.99")),
                    "vendor1"))
        .isInstanceOf(DuplicateSkuException.class);
  }

  @Test
  void createProductRecordsWhoCreatedIt() {
    when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

    ProductResponse response =
        productService.createProduct(
            new CreateProductRequest("SKU-2", "Gadget", 5, 1, new BigDecimal("4.00")), "vendor1");

    assertThat(response.createdBy()).isEqualTo("vendor1");
  }

  @Test
  void restockOnlyAddsToStockOnHand() {
    Product product = new Product("SKU-1", "Widget", 2, 0, new BigDecimal("9.99"));
    product.setQuantityReserved(1);
    product.setCreatedBy("vendor1");
    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));

    ProductResponse response = productService.restock("SKU-1", 10, "vendor1", false);

    assertThat(response.quantityOnHand()).isEqualTo(12);
    assertThat(product.getQuantityReserved()).isEqualTo(1);
    verify(productRepository).save(product);
  }

  @Test
  void aVendorCannotRestockAnotherVendorsProduct() {
    Product product = new Product("SKU-1", "Widget", 2, 0, new BigDecimal("9.99"));
    product.setCreatedBy("vendor1");
    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> productService.restock("SKU-1", 5, "vendor2", false))
        .isInstanceOf(ProductOwnershipException.class);
    assertThat(product.getQuantityOnHand()).isEqualTo(2);
  }

  @Test
  void onlyAnAdminCanRestockAProductWithNoRecordedOwner() {
    Product product = new Product("SKU-1", "Widget", 2, 0, new BigDecimal("9.99"));
    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> productService.restock("SKU-1", 5, "vendor1", false))
        .isInstanceOf(ProductOwnershipException.class);
    assertThat(productService.restock("SKU-1", 5, "admin", true).quantityOnHand()).isEqualTo(7);
  }

  @Test
  void restockCannotPushStockPastTheMaximum() {
    Product product =
        new Product("SKU-1", "Widget", InventoryLimits.MAX_STOCK - 1, 0, new BigDecimal("9.99"));
    when(productRepository.findWithLockBySku("SKU-1")).thenReturn(Optional.of(product));

    assertThatThrownBy(() -> productService.restock("SKU-1", 2, "admin", true))
        .isInstanceOf(StockLimitExceededException.class);
    assertThat(product.getQuantityOnHand()).isEqualTo(InventoryLimits.MAX_STOCK - 1);
  }
}
