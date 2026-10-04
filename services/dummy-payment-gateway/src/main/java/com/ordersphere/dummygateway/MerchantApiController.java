package com.ordersphere.dummygateway;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-to-server API for the merchant (payment-service); authenticated by {@link
 * ApiKeyInterceptor}.
 */
@RestController
@RequestMapping("/api")
public class MerchantApiController {

  private final CheckoutService checkoutService;

  public MerchantApiController(CheckoutService checkoutService) {
    this.checkoutService = checkoutService;
  }

  @PostMapping("/checkout-sessions")
  public ResponseEntity<?> createSession(@Valid @RequestBody CreateSessionRequest request) {
    CheckoutSession session =
        checkoutService.create(
            request.merchantReference(),
            request.amount(),
            request.currency(),
            request.description(),
            request.successUrl(),
            request.cancelUrl(),
            request.webhookUrl());
    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(session));
  }

  @GetMapping("/checkout-sessions/{sessionId}")
  public ResponseEntity<?> getSession(@PathVariable String sessionId) {
    return checkoutService
        .find(sessionId)
        .<ResponseEntity<?>>map(session -> ResponseEntity.ok(toResponse(session)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/refunds")
  public ResponseEntity<?> refund(@Valid @RequestBody RefundRequest request) {
    return checkoutService
        .refund(request.chargeReference())
        .<ResponseEntity<?>>map(
            refundReference ->
                ResponseEntity.ok(
                    Map.of("refundReference", refundReference, "status", "SUCCEEDED")))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Settlement report: charges and refunds in [from, to), ISO-8601 instants. */
  @GetMapping("/reports/transactions")
  public List<CheckoutService.Transaction> transactions(
      @RequestParam Instant from, @RequestParam Instant to) {
    return checkoutService.transactions(from, to);
  }

  private SessionResponse toResponse(CheckoutSession session) {
    return new SessionResponse(
        session.getId(),
        checkoutService.checkoutUrl(session),
        checkoutService.status(session),
        session.getMerchantReference(),
        session.getChargeReference(),
        session.getRefundReference(),
        session.getAmount(),
        session.getCurrency(),
        session.getExpiresAt());
  }

  public record CreateSessionRequest(
      @NotBlank String merchantReference,
      @NotNull @DecimalMin("0.01") BigDecimal amount,
      @NotBlank String currency,
      String description,
      @NotBlank String successUrl,
      @NotBlank String cancelUrl,
      String webhookUrl) {}

  public record RefundRequest(@NotBlank String chargeReference) {}

  public record SessionResponse(
      String id,
      String url,
      CheckoutSession.Status status,
      String merchantReference,
      String chargeReference,
      String refundReference,
      BigDecimal amount,
      String currency,
      Instant expiresAt) {}
}
