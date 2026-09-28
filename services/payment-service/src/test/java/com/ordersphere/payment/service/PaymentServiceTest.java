package com.ordersphere.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.events.PaymentCompletedEvent;
import com.ordersphere.events.PaymentFailedEvent;
import com.ordersphere.events.PaymentInitiatedEvent;
import com.ordersphere.events.RefundIssuedEvent;
import com.ordersphere.payment.domain.Payment;
import com.ordersphere.payment.domain.PaymentStatus;
import com.ordersphere.payment.domain.Refund;
import com.ordersphere.payment.domain.RefundStatus;
import com.ordersphere.payment.dto.CreatePaymentRequest;
import com.ordersphere.payment.dto.PaymentResponse;
import com.ordersphere.payment.dto.RefundRequest;
import com.ordersphere.payment.exception.InvalidPaymentStateException;
import com.ordersphere.payment.exception.PaymentNotFoundException;
import com.ordersphere.payment.gateway.PaymentGatewayClient;
import com.ordersphere.payment.gateway.PaymentGatewayClient.CheckoutRequest;
import com.ordersphere.payment.gateway.PaymentGatewayClient.CheckoutSession;
import com.ordersphere.payment.gateway.PaymentGatewayClient.SessionStatus;
import com.ordersphere.payment.gateway.PaymentGatewayException;
import com.ordersphere.payment.repository.PaymentRepository;
import com.ordersphere.payment.repository.RefundRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private RefundRepository refundRepository;
  @Mock private PaymentGatewayClient gatewayClient;
  @Mock private ApplicationEventPublisher eventPublisher;

  private PaymentService paymentService;

  @BeforeEach
  void setUp() {
    paymentService =
        new PaymentService(
            paymentRepository,
            refundRepository,
            gatewayClient,
            eventPublisher,
            "http://shop/",
            "http://payments/webhook");
    lenient()
        .when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(
            invocation -> {
              Payment payment = invocation.getArgument(0);
              if (payment.getId() == null) {
                payment.setId(5L);
              }
              return payment;
            });
  }

  private Payment pendingPayment() {
    Payment payment = new Payment(100L, "alice", new BigDecimal("20.00"), "USD");
    payment.setId(5L);
    payment.setCheckoutSessionId("cs_1");
    payment.setCheckoutUrl("http://gw/checkout/cs_1");
    return payment;
  }

  @Test
  void initiatePaymentOpensCheckoutSessionAndReturnsItsUrl() {
    when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());
    when(gatewayClient.createCheckoutSession(any()))
        .thenReturn(
            new CheckoutSession("cs_1", "http://gw/checkout/cs_1", SessionStatus.OPEN, null));

    PaymentResponse response =
        paymentService.initiatePayment(
            "alice", new CreatePaymentRequest(100L, new BigDecimal("20.00"), "USD"));

    assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    assertThat(response.checkoutUrl()).isEqualTo("http://gw/checkout/cs_1");
    ArgumentCaptor<CheckoutRequest> request = ArgumentCaptor.forClass(CheckoutRequest.class);
    verify(gatewayClient).createCheckoutSession(request.capture());
    assertThat(request.getValue().merchantReference()).isEqualTo("5");
    assertThat(request.getValue().amount()).isEqualByComparingTo("20.00");
    assertThat(request.getValue().successUrl())
        .isEqualTo("http://shop/?orderId=100&payment=success");
    assertThat(request.getValue().cancelUrl())
        .isEqualTo("http://shop/?orderId=100&payment=cancelled");
    assertThat(request.getValue().webhookUrl()).isEqualTo("http://payments/webhook");
    verify(eventPublisher).publishEvent(any(PaymentInitiatedEvent.class));
  }

  @Test
  void initiatePaymentIsIdempotentPerOrderId() {
    when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(pendingPayment()));

    PaymentResponse response =
        paymentService.initiatePayment(
            "alice", new CreatePaymentRequest(100L, new BigDecimal("20.00"), "USD"));

    assertThat(response.id()).isEqualTo(5L);
    assertThat(response.checkoutUrl()).isEqualTo("http://gw/checkout/cs_1");
    verify(gatewayClient, never()).createCheckoutSession(any());
  }

  @Test
  void initiatePaymentPropagatesGatewayOutage() {
    when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());
    when(gatewayClient.createCheckoutSession(any()))
        .thenThrow(new PaymentGatewayException("down", new RuntimeException()));

    assertThatThrownBy(
            () ->
                paymentService.initiatePayment(
                    "alice", new CreatePaymentRequest(100L, new BigDecimal("20.00"), "USD")))
        .isInstanceOf(PaymentGatewayException.class);
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void succeededOutcomeCompletesPaymentAndPublishesEvent() {
    Payment payment = pendingPayment();
    when(paymentRepository.findByCheckoutSessionIdForUpdate("cs_1"))
        .thenReturn(Optional.of(payment));

    paymentService.applySessionOutcome("cs_1", SessionStatus.SUCCEEDED, "ch_1");

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment.getGatewayReference()).isEqualTo("ch_1");
    verify(eventPublisher).publishEvent(any(PaymentCompletedEvent.class));
  }

  @Test
  void cancelledOutcomeFailsPaymentWithReason() {
    Payment payment = pendingPayment();
    when(paymentRepository.findByCheckoutSessionIdForUpdate("cs_1"))
        .thenReturn(Optional.of(payment));

    paymentService.applySessionOutcome("cs_1", SessionStatus.CANCELLED, null);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureReason()).isEqualTo("Payment cancelled by customer");
    verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
  }

  @Test
  void outcomeForAlreadySettledPaymentIsIgnored() {
    Payment payment = pendingPayment();
    payment.markStatus(PaymentStatus.COMPLETED);
    when(paymentRepository.findByCheckoutSessionIdForUpdate("cs_1"))
        .thenReturn(Optional.of(payment));

    paymentService.applySessionOutcome("cs_1", SessionStatus.CANCELLED, null);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void reconcileAppliesSessionStatusFromGateway() {
    Payment payment = pendingPayment();
    when(paymentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(payment));
    when(gatewayClient.getCheckoutSession("cs_1"))
        .thenReturn(Optional.of(new CheckoutSession("cs_1", null, SessionStatus.EXPIRED, null)));

    paymentService.reconcile(5L);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getFailureReason()).isEqualTo("Payment window expired");
  }

  @Test
  void reconcileLeavesOpenSessionPending() {
    Payment payment = pendingPayment();
    when(paymentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(payment));
    when(gatewayClient.getCheckoutSession("cs_1"))
        .thenReturn(Optional.of(new CheckoutSession("cs_1", null, SessionStatus.OPEN, null)));

    paymentService.reconcile(5L);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void reconcileFailsPaymentWhoseSessionTheGatewayDoesNotKnow() {
    Payment payment = pendingPayment();
    when(paymentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(payment));
    when(gatewayClient.getCheckoutSession("cs_1")).thenReturn(Optional.empty());

    paymentService.reconcile(5L);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  void reconcileLeavesPaymentPendingWhenGatewayIsUnreachable() {
    Payment payment = pendingPayment();
    when(paymentRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(payment));
    when(gatewayClient.getCheckoutSession("cs_1"))
        .thenThrow(new PaymentGatewayException("down", new RuntimeException()));

    paymentService.reconcile(5L);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  void getPaymentRejectsNonOwner() {
    when(paymentRepository.findByIdAndCustomerUsername(5L, "mallory")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> paymentService.getPayment("mallory", false, 5L))
        .isInstanceOf(PaymentNotFoundException.class);
  }

  @Test
  void refundRequiresCompletedPayment() {
    when(paymentRepository.findByIdAndCustomerUsername(5L, "alice"))
        .thenReturn(Optional.of(pendingPayment()));
    when(refundRepository.findByPaymentId(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> paymentService.refundPayment("alice", false, 5L, new RefundRequest("x")))
        .isInstanceOf(InvalidPaymentStateException.class);
  }

  @Test
  void settleRefundRefundsTheChargeAndMarksPaymentRefunded() {
    Payment payment = pendingPayment();
    payment.setGatewayReference("ch_1");
    payment.markStatus(PaymentStatus.COMPLETED);
    Refund refund = new Refund(payment, payment.getAmount(), "not happy");
    refund.setId(9L);
    when(gatewayClient.refund("ch_1")).thenReturn(Optional.of("re_1"));

    paymentService.settleRefund(refund);

    assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
    assertThat(refund.getGatewayReference()).isEqualTo("re_1");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    verify(eventPublisher).publishEvent(any(RefundIssuedEvent.class));
  }

  @Test
  void settleRefundStaysPendingWhenGatewayIsUnreachable() {
    Payment payment = pendingPayment();
    payment.setGatewayReference("ch_1");
    payment.markStatus(PaymentStatus.COMPLETED);
    Refund refund = new Refund(payment, payment.getAmount(), "not happy");
    when(gatewayClient.refund("ch_1"))
        .thenThrow(new PaymentGatewayException("down", new RuntimeException()));

    paymentService.settleRefund(refund);

    assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
  }
}
