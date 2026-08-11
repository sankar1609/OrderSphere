package com.ordersphere.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.events.PaymentCompletedEvent;
import com.ordersphere.events.PaymentFailedEvent;
import com.ordersphere.events.PaymentInitiatedEvent;
import com.ordersphere.events.RefundIssuedEvent;
import com.ordersphere.payment.domain.Payment;
import com.ordersphere.payment.domain.PaymentMethod;
import com.ordersphere.payment.domain.PaymentMethodType;
import com.ordersphere.payment.domain.PaymentStatus;
import com.ordersphere.payment.domain.Refund;
import com.ordersphere.payment.dto.CreatePaymentRequest;
import com.ordersphere.payment.dto.PaymentResponse;
import com.ordersphere.payment.dto.RefundRequest;
import com.ordersphere.payment.dto.RefundResponse;
import com.ordersphere.payment.exception.InvalidPaymentStateException;
import com.ordersphere.payment.exception.PaymentNotFoundException;
import com.ordersphere.payment.gateway.GatewayResult;
import com.ordersphere.payment.gateway.PaymentGatewayClient;
import com.ordersphere.payment.repository.PaymentMethodRepository;
import com.ordersphere.payment.repository.PaymentRepository;
import com.ordersphere.payment.repository.RefundRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private PaymentMethodRepository paymentMethodRepository;
  @Mock private RefundRepository refundRepository;
  @Mock private PaymentGatewayClient gatewayClient;
  @Mock private ApplicationEventPublisher eventPublisher;

  private PaymentService paymentService;

  @BeforeEach
  void setUp() {
    paymentService =
        new PaymentService(
            paymentRepository,
            paymentMethodRepository,
            refundRepository,
            gatewayClient,
            eventPublisher,
            3);
  }

  private PaymentMethod method(String username, String token) {
    PaymentMethod method = new PaymentMethod(username, PaymentMethodType.CARD, token);
    method.setId(1L);
    return method;
  }

  @Test
  void initiatePaymentCreatesPendingAndPublishesEvent() {
    when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());
    when(paymentMethodRepository.findByIdAndCustomerUsername(1L, "alice"))
        .thenReturn(Optional.of(method("alice", "tok-good")));

    PaymentResponse response =
        paymentService.initiatePayment(
            "alice", new CreatePaymentRequest(100L, 1L, new BigDecimal("20.00"), "USD"));

    assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
    verify(eventPublisher).publishEvent(any(PaymentInitiatedEvent.class));
  }

  @Test
  void initiatePaymentIsIdempotentPerOrderId() {
    Payment existing =
        new Payment(100L, "alice", method("alice", "tok-good"), new BigDecimal("20.00"), "USD");
    when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(existing));

    PaymentResponse response =
        paymentService.initiatePayment(
            "alice", new CreatePaymentRequest(100L, 1L, new BigDecimal("20.00"), "USD"));

    assertThat(response.orderId()).isEqualTo(100L);
    verify(paymentMethodRepository, never()).findByIdAndCustomerUsername(any(), any());
    verify(eventPublisher, never()).publishEvent(any(PaymentInitiatedEvent.class));
  }

  @Test
  void getPaymentRejectsNonOwner() {
    when(paymentRepository.findByIdAndCustomerUsername(5L, "bob")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> paymentService.getPayment("bob", false, 5L))
        .isInstanceOf(PaymentNotFoundException.class);
  }

  @Test
  void refundRequiresCompletedPayment() {
    Payment payment =
        new Payment(100L, "alice", method("alice", "tok-good"), new BigDecimal("20.00"), "USD");
    payment.setId(5L);
    when(paymentRepository.findByIdAndCustomerUsername(5L, "alice"))
        .thenReturn(Optional.of(payment));
    when(refundRepository.findByPaymentId(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> paymentService.refundPayment("alice", false, 5L, new RefundRequest("not happy")))
        .isInstanceOf(InvalidPaymentStateException.class);
  }

  @Test
  void refundIsIdempotent() {
    Payment payment =
        new Payment(100L, "alice", method("alice", "tok-good"), new BigDecimal("20.00"), "USD");
    payment.setId(5L);
    payment.markStatus(PaymentStatus.COMPLETED);
    Refund existingRefund = new Refund(payment, payment.getAmount(), "not happy");
    when(paymentRepository.findByIdAndCustomerUsername(5L, "alice"))
        .thenReturn(Optional.of(payment));
    when(refundRepository.findByPaymentId(5L)).thenReturn(Optional.of(existingRefund));

    RefundResponse response =
        paymentService.refundPayment("alice", false, 5L, new RefundRequest("not happy"));

    assertThat(response.reason()).isEqualTo("not happy");
    verify(refundRepository, never()).save(any());
  }

  @Test
  void settlePaymentMarksCompletedOnGatewaySuccess() {
    Payment payment =
        new Payment(100L, "alice", method("alice", "tok-good"), new BigDecimal("20.00"), "USD");
    when(gatewayClient.authorize("tok-good", payment.getAmount(), "USD"))
        .thenReturn(new GatewayResult(GatewayResult.GatewayOutcome.SUCCESS, "gw-ref"));

    paymentService.settlePayment(payment);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(payment.getGatewayReference()).isEqualTo("gw-ref");
    verify(eventPublisher).publishEvent(any(PaymentCompletedEvent.class));
  }

  @Test
  void settlePaymentFailsImmediatelyOnDecline() {
    Payment payment =
        new Payment(100L, "alice", method("alice", "tok-decline"), new BigDecimal("20.00"), "USD");
    when(gatewayClient.authorize("tok-decline", payment.getAmount(), "USD"))
        .thenReturn(new GatewayResult(GatewayResult.GatewayOutcome.DECLINED, null));

    paymentService.settlePayment(payment);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    assertThat(payment.getRetryCount()).isZero();
    verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
  }

  @Test
  void settlePaymentRetriesTransientFailureThenFailsAtMax() {
    Payment payment =
        new Payment(
            100L, "alice", method("alice", "tok-transient"), new BigDecimal("20.00"), "USD");
    when(gatewayClient.authorize("tok-transient", payment.getAmount(), "USD"))
        .thenReturn(new GatewayResult(GatewayResult.GatewayOutcome.TRANSIENT_FAILURE, null));

    paymentService.settlePayment(payment);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(payment.getRetryCount()).isEqualTo(1);

    paymentService.settlePayment(payment);
    assertThat(payment.getRetryCount()).isEqualTo(2);

    paymentService.settlePayment(payment);
    assertThat(payment.getRetryCount()).isEqualTo(3);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    verify(eventPublisher).publishEvent(any(PaymentFailedEvent.class));
  }

  @Test
  void settleRefundMarksCompletedAndPaymentRefunded() {
    Payment payment =
        new Payment(100L, "alice", method("alice", "tok-good"), new BigDecimal("20.00"), "USD");
    payment.setId(5L);
    payment.markStatus(PaymentStatus.COMPLETED);
    Refund refund = new Refund(payment, payment.getAmount(), "not happy");
    refund.setId(9L);
    when(gatewayClient.refund("tok-good", payment.getAmount()))
        .thenReturn(new GatewayResult(GatewayResult.GatewayOutcome.SUCCESS, "gw-refund-ref"));

    paymentService.settleRefund(refund);

    assertThat(refund.getStatus().name()).isEqualTo("COMPLETED");
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    verify(eventPublisher).publishEvent(any(RefundIssuedEvent.class));
  }
}
