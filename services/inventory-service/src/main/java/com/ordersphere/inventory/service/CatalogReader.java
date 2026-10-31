package com.ordersphere.inventory.service;

import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.dto.ProductPageResponse;
import com.ordersphere.inventory.dto.ProductResponse;
import com.ordersphere.inventory.exception.ProductNotFoundException;
import com.ordersphere.inventory.repository.ProductRepository;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

  /** A page of the catalog in SKU order, optionally only products whose SKU or name match. */
  @Transactional(readOnly = true)
  public ProductPageResponse loadPage(int page, int size, String query) {
    PageRequest pageable = PageRequest.of(page, size, Sort.by("sku"));
    Page<Product> products =
        query == null
            ? productRepository.findAll(pageable)
            : productRepository.search(likePattern(query), pageable);
    return ProductPageResponse.of(products.map(ProductResponse::from));
  }

  /** "a_b" matches the text a_b, not "a" + any character + "b". */
  private static String likePattern(String query) {
    String escaped =
        query
            .toLowerCase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    return "%" + escaped + "%";
  }

  @Transactional(readOnly = true)
  public ProductResponse load(String sku) {
    return productRepository
        .findBySku(sku)
        .map(ProductResponse::from)
        .orElseThrow(() -> new ProductNotFoundException(sku));
  }
}
