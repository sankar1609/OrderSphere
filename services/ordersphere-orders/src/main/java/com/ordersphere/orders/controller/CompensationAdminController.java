package com.ordersphere.orders.controller;

import com.ordersphere.orders.domain.Compensation;
import com.ordersphere.orders.dto.CompensationResponse;
import com.ordersphere.orders.service.CompensationService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin view of the refunds and stock releases the saga owes other services. FAILED ones were
 * rejected by the other service or ran out of retries and need a person: fix the cause, then retry.
 */
@RestController
@RequestMapping("/orders/admin/compensations")
@PreAuthorize("hasRole('ADMIN')")
public class CompensationAdminController {

  private final CompensationService compensationService;

  public CompensationAdminController(CompensationService compensationService) {
    this.compensationService = compensationService;
  }

  /** Defaults to FAILED - the ones that need attention. */
  @GetMapping
  public List<CompensationResponse> list(
      @RequestParam(defaultValue = "FAILED") Compensation.Status status) {
    return compensationService.list(status);
  }

  @GetMapping("/{id}")
  public CompensationResponse get(@PathVariable Long id) {
    return compensationService.get(id);
  }

  /** Re-queues a FAILED compensation with a fresh retry budget and attempts it right away. */
  @PostMapping("/{id}/retry")
  public CompensationResponse retry(@PathVariable Long id) {
    compensationService.retry(id);
    return compensationService.get(id);
  }
}
