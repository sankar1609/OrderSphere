package com.ordersphere.inventory.service;

import com.ordersphere.inventory.dto.ProductResponse;
import com.ordersphere.inventory.exception.ProductNotFoundException;
import com.ordersphere.inventory.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the catalog from the database. A bean of its own so that the transaction (and its database
 * connection) only starts when the catalog cache misses - a cache hit touches no database at all.
 */
@Component
public class CatalogReader {

  private final ProductRepository productRepository;

  public CatalogReader(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  @Transactional(readOnly = true)
  public List<ProductResponse> loadAll() {
    return productRepository.findAll().stream().map(ProductResponse::from).toList();
  }

  @Transactional(readOnly = true)
  public ProductResponse load(String sku) {
    return productRepository
        .findBySku(sku)
        .map(ProductResponse::from)
        .orElseThrow(() -> new ProductNotFoundException(sku));
  }
}
