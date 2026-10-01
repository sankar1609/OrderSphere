package com.ordersphere.inventory.controller;

import com.ordersphere.inventory.dto.CreateProductRequest;
import com.ordersphere.inventory.dto.ProductResponse;
import com.ordersphere.inventory.dto.RestockRequest;
import com.ordersphere.inventory.service.ProductService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory/products")
public class ProductController {

  private final ProductService productService;

  public ProductController(ProductService productService) {
    this.productService = productService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'VENDOR')")
  public ResponseEntity<ProductResponse> createProduct(
      @Valid @RequestBody CreateProductRequest request, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(productService.createProduct(request, principal.getName()));
  }

  @GetMapping
  public List<ProductResponse> listProducts() {
    return productService.listProducts();
  }

  @GetMapping("/{sku}")
  public ProductResponse getProduct(@PathVariable String sku) {
    return productService.getProduct(sku);
  }

  @PostMapping("/{sku}/restock")
  @PreAuthorize("hasAnyRole('ADMIN', 'VENDOR')")
  public ProductResponse restock(
      @PathVariable String sku, @Valid @RequestBody RestockRequest request) {
    return productService.restock(sku, request.quantity());
  }
}
