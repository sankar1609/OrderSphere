package com.ordersphere.notification.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** One page of a list: the same shape as audit-service's {@code GET /audit/entries}. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

  public static <T> PageResponse<T> of(Page<T> page) {
    return new PageResponse<>(
        page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
  }
}
