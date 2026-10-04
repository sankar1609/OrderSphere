package com.ordersphere.payment.reconciliation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Payment reconciliation (admin)",
    description = "Our payments checked against the provider's settlement report")
@RestController
@RequestMapping("/payments/admin/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class ReconciliationAdminController {

  private final ReconciliationService reconciliation;

  public ReconciliationAdminController(ReconciliationService reconciliation) {
    this.reconciliation = reconciliation;
  }

  @Operation(
      summary = "Run reconciliation now",
      description = "Checks the last `hours` (default 24, at most 31 days) against the provider.")
  @PostMapping("/runs")
  public ReconciliationRun run(@RequestParam(defaultValue = "24") @Min(1) @Max(744) int hours) {
    Instant now = Instant.now();
    return reconciliation.run(now.minus(Duration.ofHours(hours)), now);
  }

  @GetMapping("/runs")
  public List<ReconciliationRun> runs() {
    return reconciliation.recentRuns();
  }

  @GetMapping("/findings")
  public List<ReconciliationFinding> findings(
      @RequestParam(defaultValue = "OPEN") ReconciliationFinding.Status status) {
    return reconciliation.findings(status);
  }

  @Operation(
      summary = "Re-sync a finding from the provider",
      description =
          "Applies the provider's state: records a charge (the orders saga then confirms or"
              + " refunds the order), records a refund, or executes a missing refund. 409 for types"
              + " that need a person (RECORDED_NOT_CHARGED, AMOUNT_MISMATCH, UNKNOWN_SESSION).")
  @PostMapping("/findings/{id}/resync")
  public ReconciliationFinding resync(@PathVariable Long id, Principal principal) {
    return reconciliation.resync(id, principal.getName());
  }

  @Operation(summary = "Mark a finding resolved, with a note")
  @PostMapping("/findings/{id}/resolve")
  public ReconciliationFinding resolve(
      @PathVariable Long id, @Valid @RequestBody ResolveRequest request, Principal principal) {
    return reconciliation.resolve(id, principal.getName(), request.note().trim());
  }

  public record ResolveRequest(@NotBlank @Size(max = 1000) String note) {}
}
