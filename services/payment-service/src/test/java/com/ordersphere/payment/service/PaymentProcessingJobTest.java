package com.ordersphere.payment.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ordersphere.payment.domain.Payment;
import com.ordersphere.payment.domain.PaymentMethod;
import com.ordersphere.payment.domain.PaymentMethodType;
import com.ordersphere.payment.domain.PaymentStatus;
import com.ordersphere.payment.domain.Refund;
import com.ordersphere.payment.domain.RefundStatus;
import com.ordersphere.payment.repository.PaymentRepository;
import com.ordersphere.payment.repository.RefundRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingJobTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private RefundRepository refundRepository;
  @Mock private PaymentService paymentService;

  @Test
  void processPendingPaymentsSettlesEachPendingPayment() {
    PaymentMethod method = new PaymentMethod("alice", PaymentMethodType.CARD, "tok-good");
    Payment pending = new Payment(100L, "alice", method, new BigDecimal("20.00"), "USD");
    when(paymentRepository.findByStatus(PaymentStatus.PENDING)).thenReturn(List.of(pending));

    PaymentProcessingJob job =
        new PaymentProcessingJob(paymentRepository, refundRepository, paymentService);
    job.processPendingPayments();

    verify(paymentService).settlePayment(pending);
  }

  @Test
  void processPendingPaymentsDoesNothingWhenNonePending() {
    when(paymentRepository.findByStatus(PaymentStatus.PENDING)).thenReturn(List.of());

    PaymentProcessingJob job =
        new PaymentProcessingJob(paymentRepository, refundRepository, paymentService);
    job.processPendingPayments();

    verifyNoInteractions(paymentService);
  }

  @Test
  void processPendingRefundsSettlesEachPendingRefund() {
    PaymentMethod method = new PaymentMethod("alice", PaymentMethodType.CARD, "tok-good");
    Payment payment = new Payment(100L, "alice", method, new BigDecimal("20.00"), "USD");
    Refund pending = new Refund(payment, payment.getAmount(), "not happy");
    when(refundRepository.findByStatus(RefundStatus.PENDING)).thenReturn(List.of(pending));

    PaymentProcessingJob job =
        new PaymentProcessingJob(paymentRepository, refundRepository, paymentService);
    job.processPendingRefunds();

    verify(paymentService).settleRefund(pending);
  }
}
