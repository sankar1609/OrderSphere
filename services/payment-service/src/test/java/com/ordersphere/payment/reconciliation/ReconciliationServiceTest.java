package com.ordersphere.payment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.payment.domain.Payment;
import com.ordersphere.payment.domain.PaymentStatus;
import com.ordersphere.payment.gateway.PaymentGatewayClient;
import com.ordersphere.payment.gateway.PaymentGatewayClient.CheckoutSession;
import com.ordersphere.payment.gateway.PaymentGatewayClient.ProviderTransaction;
import com.ordersphere.payment.gateway.PaymentGatewayClient.SessionStatus;
import com.ordersphere.payment.reconciliation.ReconciliationFinding.Status;
import com.ordersphere.payment.reconciliation.ReconciliationFinding.Type;
import com.ordersphere.payment.repository.PaymentRepository;
import com.ordersphere.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-03T02:00:00Z");
  private static final Instant FROM = NOW.minus(Duration.ofDays(1));

  @Mock private PaymentRepository payments;
  @Mock private ReconciliationRunRepository runs;
  @Mock private ReconciliationFindingRepository findingRepository;
  @Mock private PaymentGatewayClient gateway;
  @Mock private PaymentService paymentService;

  /** Stands in for the findings table. */
  private final List<ReconciliationFinding> stored = new ArrayList<>();

  private final AtomicLong ids = new AtomicLong();
  private ReconciliationService service;

  @BeforeEach
  void setUp() {
    service =
        new ReconciliationService(
            payments,
            runs,
            findingRepository,
            gateway,
            paymentService,
            Duration.ofMinutes(15),
            Clock.fixed(NOW, ZoneOffset.UTC));
    lenient()
        .when(runs.save(any(ReconciliationRun.class)))
        .thenAnswer(
            i -> {
              ReconciliationRun run = i.getArgument(0);
              if (run.getId() == null) {
                run.setId(ids.incrementAndGet());
              }
              return run;
            });
    lenient()
        .when(findingRepository.save(any(ReconciliationFinding.class)))
        .thenAnswer(
            i -> {
              ReconciliationFinding finding = i.getArgument(0);
              if (finding.getId() == null) {
                finding.setId(ids.incrementAndGet());
                stored.add(finding);
              }
              return finding;
            });
    lenient()
        .when(findingRepository.findByTypeAndCheckoutSessionIdAndStatus(any(), any(), any()))
        .thenAnswer(
            i ->
                stored.stream()
                    .filter(
                        f ->
                            f.getType() == i.getArgument(0)
                                && f.getCheckoutSessionId().equals(i.getArgument(1))
                                && f.getStatus() == i.getArgument(2))
                    .findFirst());
    lenient()
        .when(findingRepository.findByStatus(Status.OPEN))
        .thenAnswer(i -> stored.stream().filter(f -> f.getStatus() == Status.OPEN).toList());
    lenient()
        .when(findingRepository.findById(any()))
        .thenAnswer(
            i -> stored.stream().filter(f -> f.getId().equals(i.getArgument(0))).findFirst());
    lenient()
        .when(findingRepository.existsByTypeAndCheckoutSessionIdAndResolution(any(), any(), any()))
        .thenAnswer(
            i ->
                stored.stream()
                    .anyMatch(
                        f ->
                            f.getType() == i.getArgument(0)
                                && f.getCheckoutSessionId().equals(i.getArgument(1))
                                && f.getResolution() == i.getArgument(2)));
    lenient().when(payments.findByCheckoutSessionId(any())).thenReturn(Optional.empty());
    lenient()
        .when(payments.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(FROM, NOW))
        .thenReturn(List.of());
  }

  private static Payment payment(long id, String session, PaymentStatus status, String amount) {
    Payment payment = new Payment(100 + id, "alice", new BigDecimal(amount), "USD");
    payment.setId(id);
    payment.setCheckoutSessionId(session);
    payment.setStatus(status);
    payment.setCreatedAt(NOW.minus(Duration.ofHours(3)));
    return payment;
  }

  private static ProviderTransaction charge(String session, String amount) {
    return new ProviderTransaction(
        ProviderTransaction.Type.CHARGE,
        session,
        "ch_" + session,
        null,
        new BigDecimal(amount),
        "USD",
        NOW.minus(Duration.ofHours(2)));
  }

  private static ProviderTransaction refund(String session) {
    return new ProviderTransaction(
        ProviderTransaction.Type.REFUND,
        session,
        "ch_" + session,
        "re_" + session,
        new BigDecimal("10.00"),
        "USD",
        NOW.minus(Duration.ofHours(1)));
  }

  private void provider(ProviderTransaction... report) {
    when(gateway.transactions(FROM, NOW)).thenReturn(List.of(report));
  }

  private void ours(Payment... list) {
    for (Payment p : list) {
      lenient()
          .when(payments.findByCheckoutSessionId(p.getCheckoutSessionId()))
          .thenReturn(Optional.of(p));
    }
    when(payments.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(FROM, NOW))
        .thenReturn(List.of(list));
  }

  private List<Type> openTypes() {
    return stored.stream().filter(f -> f.getStatus() == Status.OPEN).map(f -> f.getType()).toList();
  }

  @Test
  void matchingRecordsProduceNoFindings() {
    provider(charge("cs_1", "10.00"));
    ours(payment(1, "cs_1", PaymentStatus.COMPLETED, "10.00"));

    ReconciliationRun run = service.run(FROM, NOW);

    assertThat(run.getStatus()).isEqualTo(ReconciliationRun.Status.SUCCEEDED);
    assertThat(run.getTransactions()).isEqualTo(1);
    assertThat(stored).isEmpty();
  }

  @Test
  void aChargeWeRecordedAsFailedIsChargedNotRecorded() {
    provider(charge("cs_1", "10.00"));
    ours(payment(1, "cs_1", PaymentStatus.FAILED, "10.00"));

    ReconciliationRun run = service.run(FROM, NOW);

    assertThat(openTypes()).containsExactly(Type.CHARGED_NOT_RECORDED);
    assertThat(stored.get(0).getProviderChargeReference()).isEqualTo("ch_cs_1");
    assertThat(run.getFindingsOpened()).isEqualTo(1);
  }

  @Test
  void aYoungPendingPaymentIsLeftToTheWebhookAndSweep() {
    Payment pending = payment(1, "cs_1", PaymentStatus.PENDING, "10.00");
    pending.setCreatedAt(NOW.minus(Duration.ofMinutes(5)));
    provider(charge("cs_1", "10.00"));
    ours(pending);

    service.run(FROM, NOW);

    assertThat(stored).isEmpty();
  }

  @Test
  void aDifferentAmountIsAnAmountMismatch() {
    provider(charge("cs_1", "12.00"));
    ours(payment(1, "cs_1", PaymentStatus.COMPLETED, "10.00"));

    service.run(FROM, NOW);

    assertThat(openTypes()).containsExactly(Type.AMOUNT_MISMATCH);
    assertThat(stored.get(0).getDetail()).contains("12.00").contains("10.00");
  }

  @Test
  void aChargeForASessionWeDontKnowIsUnknownSession() {
    provider(charge("cs_x", "10.00"));

    service.run(FROM, NOW);

    assertThat(openTypes()).containsExactly(Type.UNKNOWN_SESSION);
  }

  @Test
  void aProviderRefundWeDontShowIsRefundNotRecorded() {
    provider(charge("cs_1", "10.00"), refund("cs_1"));
    ours(payment(1, "cs_1", PaymentStatus.COMPLETED, "10.00"));

    service.run(FROM, NOW);

    assertThat(openTypes()).containsExactly(Type.REFUND_NOT_RECORDED);
    assertThat(stored.get(0).getProviderRefundReference()).isEqualTo("re_cs_1");
  }

  @Test
  void paidOnOurSideButNotChargedAtTheProviderIsRecordedNotCharged() {
    provider();
    ours(payment(1, "cs_1", PaymentStatus.COMPLETED, "10.00"));
    when(gateway.getCheckoutSession("cs_1"))
        .thenReturn(Optional.of(new CheckoutSession("cs_1", "u", SessionStatus.OPEN, null)));

    service.run(FROM, NOW);

    assertThat(openTypes()).containsExactly(Type.RECORDED_NOT_CHARGED);
  }

  @Test
  void aChargeJustAfterTheWindowIsConfirmedBySessionLookupNotReported() {
    provider(); // charged after the window ended
    ours(payment(1, "cs_1", PaymentStatus.COMPLETED, "10.00"));
    when(gateway.getCheckoutSession("cs_1"))
        .thenReturn(
            Optional.of(new CheckoutSession("cs_1", "u", SessionStatus.SUCCEEDED, "ch_cs_1")));

    service.run(FROM, NOW);

    assertThat(stored).isEmpty();
  }

  @Test
  void refundedOnOurSideButNotAtTheProviderIsRefundNotExecuted() {
    provider(charge("cs_1", "10.00"));
    ours(payment(1, "cs_1", PaymentStatus.REFUNDED, "10.00"));
    when(gateway.getCheckoutSession("cs_1"))
        .thenReturn(
            Optional.of(
                new CheckoutSession("cs_1", "u", SessionStatus.SUCCEEDED, "ch_cs_1", null)));

    service.run(FROM, NOW);

    assertThat(openTypes()).containsExactly(Type.REFUND_NOT_EXECUTED);
    assertThat(stored.get(0).getProviderChargeReference()).isEqualTo("ch_cs_1");
  }

  @Test
  void aMismatchSeenAgainUpdatesTheOpenFindingAndClearsOnceFixed() {
    Payment payment = payment(1, "cs_1", PaymentStatus.FAILED, "10.00");
    provider(charge("cs_1", "10.00"));
    ours(payment);

    service.run(FROM, NOW);
    ReconciliationRun second = service.run(FROM, NOW);

    assertThat(stored).hasSize(1);
    assertThat(second.getFindingsOpened()).isZero();

    payment.setStatus(PaymentStatus.COMPLETED); // fixed outside reconciliation
    ReconciliationRun third = service.run(FROM, NOW);

    assertThat(third.getFindingsCleared()).isEqualTo(1);
    assertThat(stored.get(0).getStatus()).isEqualTo(Status.RESOLVED);
    assertThat(stored.get(0).getResolution()).isEqualTo(ReconciliationFinding.Resolution.CLEARED);
  }

  @Test
  void aProviderOutageMarksTheRunFailedInsteadOfThrowing() {
    when(gateway.transactions(FROM, NOW)).thenThrow(new RuntimeException("provider down"));

    ReconciliationRun run = service.run(FROM, NOW);

    assertThat(run.getStatus()).isEqualTo(ReconciliationRun.Status.FAILED);
    assertThat(run.getError()).isEqualTo("provider down");
  }

  @Test
  void resyncAppliesTheProvidersStateThroughThePaymentService() {
    provider(charge("cs_1", "10.00"), refund("cs_2"));
    ours(
        payment(1, "cs_1", PaymentStatus.FAILED, "10.00"),
        payment(2, "cs_2", PaymentStatus.COMPLETED, "10.00"));
    when(payments.findByCheckoutSessionId("cs_2"))
        .thenReturn(Optional.of(payment(2, "cs_2", PaymentStatus.COMPLETED, "10.00")));
    service.run(FROM, NOW);
    ReconciliationFinding charged = byType(Type.CHARGED_NOT_RECORDED);
    ReconciliationFinding refunded = byType(Type.REFUND_NOT_RECORDED);

    service.resync(charged.getId(), "admin");
    service.resync(refunded.getId(), "admin");

    verify(paymentService).recordProviderCharge(1L, "ch_cs_1");
    verify(paymentService).recordProviderRefund(2L, "re_cs_2");
    assertThat(charged.getResolution()).isEqualTo(ReconciliationFinding.Resolution.RESYNCED);
    assertThatThrownBy(() -> service.resync(charged.getId(), "admin"))
        .isInstanceOf(ReconciliationService.FindingStateException.class);
  }

  @Test
  void typesThatNeedAPersonCanOnlyBeResolvedWithANote() {
    provider(charge("cs_1", "12.00"));
    ours(payment(1, "cs_1", PaymentStatus.COMPLETED, "10.00"));
    service.run(FROM, NOW);
    ReconciliationFinding mismatch = byType(Type.AMOUNT_MISMATCH);

    assertThatThrownBy(() -> service.resync(mismatch.getId(), "admin"))
        .isInstanceOf(ReconciliationService.FindingStateException.class);
    verify(paymentService, never()).recordProviderCharge(any(), any());

    service.resolve(mismatch.getId(), "admin", "Partial capture agreed with the customer");

    assertThat(mismatch.getStatus()).isEqualTo(Status.RESOLVED);
    assertThat(mismatch.getResolution()).isEqualTo(ReconciliationFinding.Resolution.NOTE);
    assertThat(mismatch.getResolvedBy()).isEqualTo("admin");
  }

  private ReconciliationFinding byType(Type type) {
    return stored.stream().filter(f -> f.getType() == type).findFirst().orElseThrow();
  }

  @Test
  void aMismatchAnAdminClosedWithANoteIsNotRaisedAgain() {
    provider(charge("cs_1", "12.00"));
    ours(payment(1, "cs_1", PaymentStatus.COMPLETED, "10.00"));
    service.run(FROM, NOW);
    service.resolve(byType(Type.AMOUNT_MISMATCH).getId(), "admin", "Known partial capture");

    ReconciliationRun next = service.run(FROM, NOW);

    assertThat(next.getFindingsOpened()).isZero();
    assertThat(stored).hasSize(1);
  }

  @Test
  void aMismatchThatComesBackAfterAReSyncIsRaisedAgain() {
    Payment payment = payment(1, "cs_1", PaymentStatus.FAILED, "10.00");
    provider(charge("cs_1", "10.00"));
    ours(payment);
    service.run(FROM, NOW);
    service.resync(byType(Type.CHARGED_NOT_RECORDED).getId(), "admin");

    ReconciliationRun next = service.run(FROM, NOW); // still FAILED here (re-sync was mocked)

    assertThat(next.getFindingsOpened()).isEqualTo(1);
  }
}
