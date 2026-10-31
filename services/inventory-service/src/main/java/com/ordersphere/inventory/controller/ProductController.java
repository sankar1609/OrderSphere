package com.ordersphere.inventory.controller;

import com.ordersphere.inventory.dto.CreateProductRequest;
import com.ordersphere.inventory.dto.ProductPageResponse;
import com.ordersphere.inventory.dto.ProductResponse;
import com.ordersphere.inventory.dto.RestockRequest;
import com.ordersphere.inventory.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Products", description = "Catalog, prices and stock")
@Validated
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

  // The two read endpoints return ready-made JSON (possibly straight from the catalog cache), so
  // their response schema is declared by hand.
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ProductPageResponse.class)))
  @Operation(
      summary = "The catalog in SKU order, a page at a time",
      description =
          "q (optional) keeps the products whose SKU or name contains it, ignoring case."
              + " Pages without q come from the catalog cache.")
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> listProducts(
      @RequestParam(defaultValue = "0") @Min(0) @Max(10_000_000) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
      @RequestParam(required = false) @Size(max = 255) String q) {
    String query = q == null || q.isBlank() ? null : q.trim();
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(productService.listProductsJson(page, size, query));
  }

  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ProductResponse.class)))
  @GetMapping(value = "/{sku}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> getProduct(@PathVariable String sku) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(productService.getProductJson(sku));
  }

  @PostMapping("/{sku}/restock")
  @PreAuthorize("hasAnyRole('ADMIN', 'VENDOR')")
  public ProductResponse restock(
      @PathVariable String sku,
      @Valid @RequestBody RestockRequest request,
      Authentication authentication) {
    boolean isAdmin =
        authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    return productService.restock(sku, request.quantity(), authentication.getName(), isAdmin);
  }
}
