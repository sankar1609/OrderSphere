package com.ordersphere.inventory.dto;

import com.ordersphere.inventory.domain.Product;
import java.math.BigDecimal;

public record ProductResponse(
    Long id,
    String sku,
    String name,
    int quantityOnHand,
    int quantityReserved,
    int availableQuantity,
    int reorderThreshold,
    BigDecimal unitPrice) {

  public static ProductResponse from(Product product) {
    return new ProductResponse(
        product.getId(),
        product.getSku(),
        product.getName(),
        product.getQuantityOnHand(),
        product.getQuantityReserved(),
        product.getAvailableQuantity(),
        product.getReorderThreshold(),
        product.getUnitPrice());
  }
}
