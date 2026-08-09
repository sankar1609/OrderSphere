package com.ordersphere.inventory.dto;

import com.ordersphere.inventory.domain.Product;

public record ProductResponse(
    Long id,
    String sku,
    String name,
    int quantityOnHand,
    int quantityReserved,
    int availableQuantity,
    int reorderThreshold) {

  public static ProductResponse from(Product product) {
    return new ProductResponse(
        product.getId(),
        product.getSku(),
        product.getName(),
        product.getQuantityOnHand(),
        product.getQuantityReserved(),
        product.getAvailableQuantity(),
        product.getReorderThreshold());
  }
}
