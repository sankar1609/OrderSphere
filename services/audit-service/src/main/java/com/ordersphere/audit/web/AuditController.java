package com.ordersphere.audit.web;

import com.ordersphere.audit.chain.AuditVerifier;
import com.ordersphere.audit.chain.VerificationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The audit log, read-only. For auditors and admins: it shows every user's activity, so customers
 * and vendors get 403. There is deliberately no endpoint that writes, edits or deletes an entry -
 * entries come only from the event exchange.
 */
@Tag(name = "Audit", description = "Tamper-evident log of every domain event")
@Validated
@RestController
@RequestMapping("/audit")
@PreAuthorize("hasAnyRole('AUDITOR', 'ADMIN')")
public class AuditController {

  private final AuditQueryService queries;
  private final AuditVerifier verifier;

  public AuditController(AuditQueryService queries, AuditVerifier verifier) {
    this.queries = queries;
    this.verifier = verifier;
  }

  @Operation(
      summary = "Search the audit log",
      description = "Newest first. Every filter is optional and they combine with AND.")
  @GetMapping("/entries")
  public AuditPageResponse search(
      @RequestParam(required = false) Long orderId,
      @Parameter(description = "Exact event type, e.g. OrderConfirmedEvent")
          @RequestParam(required = false)
          String eventType,
      @Parameter(description = "Who the publishing service was acting for")
          @RequestParam(required = false)
          String actor,
      @Parameter(description = "The user the event is about") @RequestParam(required = false)
          String username,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
    return queries.search(orderId, eventType, actor, username, from, to, page, size);
  }

  @Operation(summary = "One entry, with the event's payload and its hashes")
  @GetMapping("/entries/{seq}")
  public AuditEntryResponse get(@PathVariable long seq) {
    return queries.get(seq);
  }

  @Operation(summary = "Everything recorded about one order, oldest first")
  @GetMapping("/orders/{orderId}")
  public List<AuditEntryResponse> orderTimeline(@PathVariable long orderId) {
    return queries.orderTimeline(orderId);
  }

  @Operation(
      summary = "Verify the chain now",
      description =
          "Recomputes every entry's hash and checks the links, the head and the anchors. "
              + "If valid is false, firstBrokenSeq is where the stored chain stops matching.")
  @PostMapping("/verify")
  public VerificationResult verify() {
    return verifier.verify();
  }

  @Operation(summary = "Chain length, head, last verification and last anchor")
  @GetMapping("/status")
  public AuditStatusResponse status() {
    return queries.status();
  }
}
