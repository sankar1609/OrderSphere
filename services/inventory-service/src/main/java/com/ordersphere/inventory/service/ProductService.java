package com.ordersphere.inventory.service;

import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.dto.CreateProductRequest;
import com.ordersphere.inventory.dto.InventoryLimits;
import com.ordersphere.inventory.dto.ProductResponse;
import com.ordersphere.inventory.exception.DuplicateSkuException;
import com.ordersphere.inventory.exception.ProductNotFoundException;
import com.ordersphere.inventory.exception.ProductOwnershipException;
import com.ordersphere.inventory.exception.StockLimitExceededException;
import com.ordersphere.inventory.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

  private final ProductRepository productRepository;

  public ProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  public ProductResponse createProduct(CreateProductRequest request, String createdBy) {
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
    product.setCreatedBy(createdBy);
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

  /**
   * Vendors may restock only products they created; admins any. Products from before ownership was
   * recorded have no creator, so only an admin can restock those.
   */
  @Transactional
  public ProductResponse restock(String sku, int quantity, String username, boolean isAdmin) {
    Product product =
        productRepository
            .findWithLockBySku(sku)
            .orElseThrow(() -> new ProductNotFoundException(sku));
    if (!isAdmin && !username.equals(product.getCreatedBy())) {
      throw new ProductOwnershipException(sku);
    }
    long onHand = (long) product.getQuantityOnHand() + quantity;
    if (onHand > InventoryLimits.MAX_STOCK) {
      throw new StockLimitExceededException(sku, InventoryLimits.MAX_STOCK);
    }
    product.setQuantityOnHand((int) onHand);
    productRepository.save(product);

    return ProductResponse.from(product);
  }

  private Product findProductOrThrow(String sku) {
    return productRepository.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
  }
}
