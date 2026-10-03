package com.ordersphere.dummygateway;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout sessions and test-card rules. Sessions are stored in gateway_db, so they survive a
 * restart and the provider can report its charges and refunds for reconciliation. Every change
 * re-reads the session under a row lock, so concurrent pay/cancel/refund requests serialize.
 */
@Service
@Transactional
public class CheckoutService {

  /** Always succeeds. */
  public static final String SUCCESS_CARD = "4242424242424242";

  /** Always declined - the customer can retry with another card on the same page. */
  public static final String DECLINE_CARD = "4000000000000002";

  private final CheckoutSessionRepository repository;
  private final GatewayProperties properties;
  private final WebhookSender webhookSender;
  private final Clock clock;

  public CheckoutService(
      CheckoutSessionRepository repository,
      GatewayProperties properties,
      WebhookSender webhookSender,
      Clock clock) {
    this.repository = repository;
    this.properties = properties;
    this.webhookSender = webhookSender;
    this.clock = clock;
  }

  public CheckoutSession create(
      String merchantReference,
      BigDecimal amount,
      String currency,
      String description,
      String successUrl,
      String cancelUrl,
      String webhookUrl) {
    CheckoutSession session =
        new CheckoutSession(
            "cs_" + UUID.randomUUID().toString().replace("-", ""),
            merchantReference,
            amount,
            currency,
            description,
            successUrl,
            cancelUrl,
            webhookUrl,
            clock.instant(),
            clock.instant().plus(properties.sessionTtl()));
    return repository.save(session);
  }

  @Transactional(readOnly = true)
  public Optional<CheckoutSession> find(String sessionId) {
    return repository.findById(sessionId);
  }

  public String checkoutUrl(CheckoutSession session) {
    return properties.publicUrl() + "/checkout/" + session.getId();
  }

  public CheckoutSession.Status status(CheckoutSession session) {
    return session.status(clock.instant());
  }

  /**
   * Attempts a charge. A decline or invalid card leaves the session OPEN so the customer can try
   * again; only a successful charge completes it (and notifies the merchant).
   */
  public PayResult pay(
      CheckoutSession requested, String cardNumber, String expiry, String cvc, String name) {
    CheckoutSession session = lock(requested);
    Instant now = clock.instant();
    if (session.status(now) != CheckoutSession.Status.OPEN) {
      return PayResult.notPayable(session.status(now));
    }

    String card = cardNumber == null ? "" : cardNumber.replaceAll("[\\s-]", "");
    if (name == null || name.isBlank()) {
      return PayResult.rejected("Enter the name on the card.");
    }
    if (!card.matches("\\d{12,19}") || !passesLuhn(card)) {
      return PayResult.rejected("That card number isn't valid.");
    }
    if (!isFutureExpiry(expiry, now)) {
      return PayResult.rejected("Enter a valid expiry date (MM/YY) that hasn't passed.");
    }
    if (cvc == null || !cvc.trim().matches("\\d{3,4}")) {
      return PayResult.rejected("Enter the 3 or 4 digit security code.");
    }
    if (!card.equals(SUCCESS_CARD)) {
      return PayResult.rejected("Your card was declined. Try a different card.");
    }

    String chargeReference = "ch_" + UUID.randomUUID().toString().replace("-", "");
    if (!session.complete(CheckoutSession.Status.SUCCEEDED, chargeReference, now)) {
      return PayResult.notPayable(session.status(now));
    }
    repository.save(session);
    webhookSender.send(session, "checkout.session.completed");
    return PayResult.succeeded();
  }

  /** The customer abandoned checkout. Returns false if the session was no longer OPEN. */
  public boolean cancel(CheckoutSession requested) {
    CheckoutSession session = lock(requested);
    if (!session.complete(CheckoutSession.Status.CANCELLED, null, clock.instant())) {
      return false;
    }
    repository.save(session);
    webhookSender.send(session, "checkout.session.cancelled");
    return true;
  }

  /** Refunds a completed charge in full; idempotent per charge. Empty if the charge is unknown. */
  public Optional<String> refund(String chargeReference) {
    return repository
        .findByChargeReferenceForUpdate(chargeReference)
        .map(
            session -> {
              String refundReference =
                  session.refund(
                      "re_" + UUID.randomUUID().toString().replace("-", ""), clock.instant());
              repository.save(session);
              return refundReference;
            });
  }

  /**
   * The settlement report: every charge and refund that happened in [from, to), oldest first - what
   * a merchant reconciles its own records against.
   */
  @Transactional(readOnly = true)
  public List<Transaction> transactions(Instant from, Instant to) {
    List<Transaction> report = new ArrayList<>();
    for (CheckoutSession s :
        repository.findByChargedAtGreaterThanEqualAndChargedAtLessThanOrderByChargedAt(from, to)) {
      report.add(Transaction.of(Transaction.Type.CHARGE, s, s.getChargedAt()));
    }
    for (CheckoutSession s :
        repository.findByRefundedAtGreaterThanEqualAndRefundedAtLessThanOrderByRefundedAt(
            from, to)) {
      report.add(Transaction.of(Transaction.Type.REFUND, s, s.getRefundedAt()));
    }
    report.sort(Comparator.comparing(Transaction::occurredAt));
    return report;
  }

  /** The current, row-locked copy of the session - the same object when it's already managed. */
  private CheckoutSession lock(CheckoutSession session) {
    return repository.findByIdForUpdate(session.getId()).orElse(session);
  }

  /** One line of the settlement report. */
  public record Transaction(
      Type type,
      String sessionId,
      String merchantReference,
      String chargeReference,
      String refundReference,
      BigDecimal amount,
      String currency,
      Instant occurredAt) {

    public enum Type {
      CHARGE,
      REFUND
    }

    static Transaction of(Type type, CheckoutSession s, Instant occurredAt) {
      return new Transaction(
          type,
          s.getId(),
          s.getMerchantReference(),
          s.getChargeReference(),
          type == Type.REFUND ? s.getRefundReference() : null,
          s.getAmount(),
          s.getCurrency(),
          occurredAt);
    }
  }

  static boolean passesLuhn(String digits) {
    int sum = 0;
    boolean doubleIt = false;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int d = digits.charAt(i) - '0';
      if (doubleIt) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
      doubleIt = !doubleIt;
    }
    return sum % 10 == 0;
  }

  private boolean isFutureExpiry(String expiry, Instant now) {
    if (expiry == null || !expiry.trim().matches("(0[1-9]|1[0-2])\\s*/\\s*\\d{2}")) {
      return false;
    }
    String[] parts = expiry.trim().split("\\s*/\\s*");
    YearMonth cardExpiry =
        YearMonth.of(2000 + Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
    return !cardExpiry.isBefore(YearMonth.from(now.atZone(clock.getZone())));
  }

  public record PayResult(Outcome outcome, String message, CheckoutSession.Status status) {

    public enum Outcome {
      SUCCEEDED,
      REJECTED,
      NOT_PAYABLE
    }

    static PayResult succeeded() {
      return new PayResult(Outcome.SUCCEEDED, null, CheckoutSession.Status.SUCCEEDED);
    }

    static PayResult rejected(String message) {
      return new PayResult(Outcome.REJECTED, message, CheckoutSession.Status.OPEN);
    }

    static PayResult notPayable(CheckoutSession.Status status) {
      return new PayResult(Outcome.NOT_PAYABLE, null, status);
    }
  }
}
