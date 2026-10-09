package com.ordersphere.payment.service;

import com.ordersphere.payment.domain.Payment;
import com.ordersphere.payment.domain.PaymentStatus;
import com.ordersphere.payment.domain.Refund;
import com.ordersphere.payment.domain.RefundStatus;
import com.ordersphere.payment.logging.PaymentLogContext;
import com.ordersphere.payment.repository.PaymentRepository;
import com.ordersphere.payment.repository.RefundRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentProcessingJob {

  private final PaymentRepository paymentRepository;
  private final RefundRepository refundRepository;
  private final PaymentService paymentService;

  public PaymentProcessingJob(
      PaymentRepository paymentRepository,
      RefundRepository refundRepository,
      PaymentService paymentService) {
    this.paymentRepository = paymentRepository;
    this.refundRepository = refundRepository;
    this.paymentService = paymentService;
  }

  @Scheduled(fixedDelayString = "${payment.processing.sweep-interval-ms}")
  public void sweep() {
    processPendingPayments();
    processPendingRefunds();
  }

  /**
   * Reconciles PENDING payments with the provider: catches outcomes whose webhook was lost and
   * payments whose checkout expired unpaid.
   */
  public void processPendingPayments() {
    for (Payment payment : paymentRepository.findByStatus(PaymentStatus.PENDING)) {
      try (var logContext = PaymentLogContext.forPayment(payment.getId(), payment.getOrderId())) {
        paymentService.reconcile(payment.getId());
      }
    }
  }

  public void processPendingRefunds() {
    for (Refund refund : refundRepository.findByStatus(RefundStatus.PENDING)) {
      Payment payment = refund.getPayment();
      try (var logContext = PaymentLogContext.forPayment(payment.getId(), payment.getOrderId())) {
        paymentService.settleRefund(refund);
      }
    }
  }
}
