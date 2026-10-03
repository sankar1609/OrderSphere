package com.ordersphere.payment.reconciliation;

import com.ordersphere.payment.domain.Payment;
import com.ordersphere.payment.domain.PaymentStatus;
import com.ordersphere.payment.gateway.PaymentGatewayClient;
import com.ordersphere.payment.gateway.PaymentGatewayClient.CheckoutSession;
import com.ordersphere.payment.gateway.PaymentGatewayClient.ProviderTransaction;
import com.ordersphere.payment.gateway.PaymentGatewayClient.SessionStatus;
import com.ordersphere.payment.reconciliation.ReconciliationFinding.Resolution;
import com.ordersphere.payment.reconciliation.ReconciliationFinding.Status;
import com.ordersphere.payment.reconciliation.ReconciliationFinding.Type;
import com.ordersphere.payment.repository.PaymentRepository;
import com.ordersphere.payment.service.PaymentService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares our payments and refunds with the provider's records and keeps a finding for each
 * disagreement. Two directions:
 *
 * <ul>
 *   <li>every charge and refund in the provider's settlement report for the window must match one
 *       of our payments (found: CHARGED_NOT_RECORDED, AMOUNT_MISMATCH, REFUND_NOT_RECORDED,
 *       UNKNOWN_SESSION);
 *   <li>every payment we created in the window and consider COMPLETED/REFUNDED must be charged (and
 *       refunded) at the provider - checked by session lookup, not the report, so a payment near
 *       the window's end whose charge falls just after it isn't misreported (RECORDED_NOT_CHARGED,
 *       REFUND_NOT_EXECUTED).
 * </ul>
 *
 * Nothing about money changes here: findings wait for an admin to re-sync or resolve them. An open
 * finding a later run sees in agreement is closed automatically (CLEARED).
 */
@Service
public class ReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

  private final PaymentRepository payments;
  private final ReconciliationRunRepository runs;
  private final ReconciliationFindingRepository findings;
  private final PaymentGatewayClient gateway;
  private final PaymentService paymentService;
  private final Duration pendingGrace;
  private final Clock clock;

  @Autowired
  public ReconciliationService(
      PaymentRepository payments,
      ReconciliationRunRepository runs,
      ReconciliationFindingRepository findings,
      PaymentGatewayClient gateway,
      PaymentService paymentService,
      @Value("${payment.reconciliation.pending-grace:PT15M}") Duration pendingGrace) {
    this(payments, runs, findings, gateway, paymentService, pendingGrace, Clock.systemUTC());
  }

  ReconciliationService(
      PaymentRepository payments,
      ReconciliationRunRepository runs,
      ReconciliationFindingRepository findings,
      PaymentGatewayClient gateway,
      PaymentService paymentService,
      Duration pendingGrace,
      Clock clock) {
    this.payments = payments;
    this.runs = runs;
    this.findings = findings;
    this.gateway = gateway;
    this.paymentService = paymentService;
    this.pendingGrace = pendingGrace;
    this.clock = clock;
  }

  /** Reconciles [from, to). A failure is recorded on the run rather than thrown. */
  public ReconciliationRun run(Instant from, Instant to) {
    ReconciliationRun run = runs.save(new ReconciliationRun(from, to, clock.instant()));
    try {
      compare(run, from, to);
      run.setStatus(ReconciliationRun.Status.SUCCEEDED);
    } catch (RuntimeException ex) {
      log.error("Reconciliation run {} failed: {}", run.getId(), ex.getMessage());
      run.setStatus(ReconciliationRun.Status.FAILED);
      run.setError(truncate(ex.getMessage()));
    }
    run.setFinishedAt(clock.instant());
    return runs.save(run);
  }

  private void compare(ReconciliationRun run, Instant from, Instant to) {
    List<ProviderTransaction> report = gateway.transactions(from, to);
    run.setTransactions(report.size());
    Map<String, ProviderTransaction> charges = new HashMap<>();
    Map<String, ProviderTransaction> refunds = new HashMap<>();
    for (ProviderTransaction tx : report) {
      (tx.type() == ProviderTransaction.Type.CHARGE ? charges : refunds).put(tx.sessionId(), tx);
    }
    Set<String> examined = new HashSet<>();
    Set<String> mismatched = new HashSet<>(); // "TYPE:session"

    // 1. Everything the provider charged must be a payment of ours, for the same money.
    for (ProviderTransaction charge : charges.values()) {
      examined.add(charge.sessionId());
      Optional<Payment> found = payments.findByCheckoutSessionId(charge.sessionId());
      if (found.isEmpty()) {
        record(
            run,
            mismatched,
            Type.UNKNOWN_SESSION,
            null,
            charge,
            "The provider charged a" + " session we have no payment for");
        continue;
      }
      Payment payment = found.get();
      if (isSettled(payment)) {
        if (payment.getAmount().compareTo(charge.amount()) != 0
            || !payment.getCurrency().equals(charge.currency())) {
          record(
              run,
              mismatched,
              Type.AMOUNT_MISMATCH,
              payment,
              charge,
              "Charged "
                  + charge.amount()
                  + " "
                  + charge.currency()
                  + ", we recorded "
                  + payment.getAmount()
                  + " "
                  + payment.getCurrency());
        }
      } else if (!isYoungPending(payment)) {
        record(
            run,
            mismatched,
            Type.CHARGED_NOT_RECORDED,
            payment,
            charge,
            "The provider" + " charged the card but our payment is " + payment.getStatus());
      }
    }

    // 2. Everything the provider refunded must be refunded on our side too.
    for (ProviderTransaction refund : refunds.values()) {
      examined.add(refund.sessionId());
      payments
          .findByCheckoutSessionId(refund.sessionId())
          .filter(payment -> payment.getStatus() != PaymentStatus.REFUNDED)
          .ifPresent(
              payment ->
                  record(
                      run,
                      mismatched,
                      Type.REFUND_NOT_RECORDED,
                      payment,
                      refund,
                      "The"
                          + " provider refunded the charge but our payment is "
                          + payment.getStatus()));
    }

    // 3. Everything we consider paid (or refunded) must have happened at the provider.
    List<Payment> ours = payments.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
    run.setPaymentsChecked(ours.size());
    for (Payment payment : ours) {
      if (payment.getCheckoutSessionId() == null || !isSettled(payment)) {
        continue;
      }
      String sessionId = payment.getCheckoutSessionId();
      examined.add(sessionId);
      boolean chargeConfirmed = charges.containsKey(sessionId);
      boolean refundConfirmed =
          payment.getStatus() != PaymentStatus.REFUNDED || refunds.containsKey(sessionId);
      if (chargeConfirmed && refundConfirmed) {
        continue;
      }
      Optional<CheckoutSession> session = gateway.getCheckoutSession(sessionId);
      if (session.isEmpty() || session.get().status() != SessionStatus.SUCCEEDED) {
        record(
            run,
            mismatched,
            Type.RECORDED_NOT_CHARGED,
            payment,
            null,
            "We recorded "
                + payment.getStatus()
                + " but the provider's session is "
                + session.map(s -> s.status().name()).orElse("unknown"));
      } else if (payment.getStatus() == PaymentStatus.REFUNDED
          && session.get().refundReference() == null) {
        ReconciliationFinding finding =
            record(
                run,
                mismatched,
                Type.REFUND_NOT_EXECUTED,
                payment,
                null,
                "We recorded"
                    + " REFUNDED but the provider never refunded charge "
                    + session.get().chargeReference());
        if (finding != null) {
          finding.setProviderChargeReference(session.get().chargeReference());
          findings.save(finding);
        }
      }
    }

    // 4. Open findings for sessions checked this run that are no longer mismatched: cleared.
    for (ReconciliationFinding open : findings.findByStatus(Status.OPEN)) {
      String key = open.getType() + ":" + open.getCheckoutSessionId();
      if (examined.contains(open.getCheckoutSessionId()) && !mismatched.contains(key)) {
        open.resolve(
            Resolution.CLEARED,
            "reconciliation",
            "Records agree as of run " + run.getId(),
            clock.instant());
        findings.save(open);
        run.setFindingsCleared(run.getFindingsCleared() + 1);
      }
    }
    log.info(
        "Reconciliation run {}: {} provider transactions, {} payments checked, {} new findings,"
            + " {} cleared",
        run.getId(),
        run.getTransactions(),
        run.getPaymentsChecked(),
        run.getFindingsOpened(),
        run.getFindingsCleared());
  }

  /** Opens a finding, or refreshes the open one for the same mismatch. */
  private ReconciliationFinding record(
      ReconciliationRun run,
      Set<String> mismatched,
      Type type,
      Payment payment,
      ProviderTransaction tx,
      String detail) {
    String sessionId = payment != null ? payment.getCheckoutSessionId() : tx.sessionId();
    mismatched.add(type + ":" + sessionId);
    Instant now = clock.instant();
    // An admin already looked at this exact mismatch and closed it with a note: don't raise it
    // again every night. (A re-sync or a clear means the records were fixed, so a recurrence is
    // new and is raised.)
    if (findings.findByTypeAndCheckoutSessionIdAndStatus(type, sessionId, Status.OPEN).isEmpty()
        && findings.existsByTypeAndCheckoutSessionIdAndResolution(
            type, sessionId, Resolution.NOTE)) {
      return null;
    }
    ReconciliationFinding finding =
        findings
            .findByTypeAndCheckoutSessionIdAndStatus(type, sessionId, Status.OPEN)
            .orElseGet(
                () -> {
                  ReconciliationFinding created = new ReconciliationFinding();
                  created.setType(type);
                  created.setCheckoutSessionId(sessionId);
                  created.setFirstSeenAt(now);
                  run.setFindingsOpened(run.getFindingsOpened() + 1);
                  return created;
                });
    finding.setRunId(run.getId());
    finding.setLastSeenAt(now);
    finding.setDetail(truncate(detail));
    if (payment != null) {
      finding.setPaymentId(payment.getId());
      finding.setOrderId(payment.getOrderId());
      finding.setOurStatus(payment.getStatus().name());
      finding.setOurAmount(payment.getAmount());
      finding.setOurCurrency(payment.getCurrency());
    }
    if (tx != null) {
      finding.setProviderStatus(
          tx.type() == ProviderTransaction.Type.CHARGE ? "CHARGED" : "REFUNDED");
      finding.setProviderAmount(tx.amount());
      finding.setProviderCurrency(tx.currency());
      finding.setProviderChargeReference(tx.chargeReference());
      finding.setProviderRefundReference(tx.refundReference());
    }
    return findings.save(finding);
  }

  @Transactional(readOnly = true)
  public List<ReconciliationRun> recentRuns() {
    return runs.findTop20ByOrderByIdDesc();
  }

  @Transactional(readOnly = true)
  public List<ReconciliationFinding> findings(Status status) {
    return findings.findByStatusOrderByLastSeenAtDesc(status);
  }

  /** Applies the provider's state to our records (see {@link Type#isResyncable()}). */
  public ReconciliationFinding resync(Long findingId, String admin) {
    ReconciliationFinding finding = openOrThrow(findingId);
    if (!finding.getType().isResyncable()) {
      throw new FindingStateException(
          finding.getType() + " can't be re-synced automatically - resolve it with a note");
    }
    switch (finding.getType()) {
      case CHARGED_NOT_RECORDED ->
          paymentService.recordProviderCharge(
              finding.getPaymentId(), finding.getProviderChargeReference());
      case REFUND_NOT_RECORDED ->
          paymentService.recordProviderRefund(
              finding.getPaymentId(), finding.getProviderRefundReference());
      case REFUND_NOT_EXECUTED -> paymentService.executeMissingRefund(finding.getPaymentId());
      default -> throw new IllegalStateException("Unhandled re-syncable type " + finding.getType());
    }
    log.warn("Reconciliation finding {} ({}) re-synced by {}", findingId, finding.getType(), admin);
    finding.resolve(Resolution.RESYNCED, admin, "Re-synced from the provider", clock.instant());
    return findings.save(finding);
  }

  /** Closes a finding an admin has dealt with outside the system. */
  public ReconciliationFinding resolve(Long findingId, String admin, String note) {
    ReconciliationFinding finding = openOrThrow(findingId);
    finding.resolve(Resolution.NOTE, admin, note, clock.instant());
    log.info(
        "Reconciliation finding {} ({}) resolved by {}: {}",
        findingId,
        finding.getType(),
        admin,
        note);
    return findings.save(finding);
  }

  private ReconciliationFinding openOrThrow(Long findingId) {
    ReconciliationFinding finding =
        findings.findById(findingId).orElseThrow(() -> new FindingNotFoundException(findingId));
    if (finding.getStatus() != Status.OPEN) {
      throw new FindingStateException("Finding " + findingId + " is already resolved");
    }
    return finding;
  }

  private static boolean isSettled(Payment payment) {
    return payment.getStatus() == PaymentStatus.COMPLETED
        || payment.getStatus() == PaymentStatus.REFUNDED;
  }

  /** A pending payment the webhook or the ~5s sweep will settle any moment - not a mismatch. */
  private boolean isYoungPending(Payment payment) {
    return payment.getStatus() == PaymentStatus.PENDING
        && payment.getCreatedAt().plus(pendingGrace).isAfter(clock.instant());
  }

  private static String truncate(String text) {
    return text == null || text.length() <= 1000 ? text : text.substring(0, 1000);
  }

  public static class FindingNotFoundException extends RuntimeException {
    public FindingNotFoundException(Long id) {
      super("No reconciliation finding with id " + id);
    }
  }

  public static class FindingStateException extends RuntimeException {
    public FindingStateException(String message) {
      super(message);
    }
  }
}
