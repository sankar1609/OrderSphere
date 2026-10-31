package com.ordersphere.inventory.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** One page of the catalog: the same shape as audit-service's {@code GET /audit/entries}. */
public record ProductPageResponse(List<ProductResponse> items, int page, int size, long total) {

  public static ProductPageResponse of(Page<ProductResponse> page) {
    return new ProductPageResponse(
        page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
  }
}
