package com.ordersphere.inventory.service;

import com.ordersphere.inventory.domain.Product;
import com.ordersphere.inventory.dto.CreateProductRequest;
import com.ordersphere.inventory.dto.ProductResponse;
import com.ordersphere.inventory.exception.DuplicateSkuException;
import com.ordersphere.inventory.exception.ProductNotFoundException;
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

    return ProductResponse.from(product);
  }

  private Product findProductOrThrow(String sku) {
    return productRepository.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
  }
}
