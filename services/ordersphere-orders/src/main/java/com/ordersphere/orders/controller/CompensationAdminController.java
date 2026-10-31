package com.ordersphere.orders.controller;

import com.ordersphere.orders.domain.Compensation;
import com.ordersphere.orders.dto.CompensationResponse;
import com.ordersphere.orders.dto.PageResponse;
import com.ordersphere.orders.service.CompensationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
@Tag(
    name = "Compensations (admin)",
    description = "Refunds and stock releases the saga owes, with retry")
@Validated
@RestController
@RequestMapping("/orders/admin/compensations")
@PreAuthorize("hasRole('ADMIN')")
public class CompensationAdminController {

  private final CompensationService compensationService;

  public CompensationAdminController(CompensationService compensationService) {
    this.compensationService = compensationService;
  }

  /** Defaults to FAILED - the ones that need attention. */
  @Operation(summary = "Compensations in a status, latest change first, a page at a time")
  @GetMapping
  public PageResponse<CompensationResponse> list(
      @RequestParam(defaultValue = "FAILED") Compensation.Status status,
      @RequestParam(defaultValue = "0") @Min(0) @Max(10_000_000) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
    return compensationService.list(status, page, size);
  }

  @GetMapping("/{id}")
  public CompensationResponse get(@PathVariable Long id) {
    return compensationService.get(id);
  }

  /** Re-queues a FAILED compensation with a fresh retry budget and attempts it right away. */
  @Operation(
      summary = "Retry a FAILED compensation",
      description =
          "Re-queues it with a fresh retry budget and attempts it now; 409 if it isn't FAILED.")
  @PostMapping("/{id}/retry")
  public CompensationResponse retry(@PathVariable Long id) {
    compensationService.retry(id);
    return compensationService.get(id);
  }
}
