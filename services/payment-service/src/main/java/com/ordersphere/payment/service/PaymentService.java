package com.ordersphere.payment.service;

import com.ordersphere.events.PaymentCompletedEvent;
import com.ordersphere.events.PaymentFailedEvent;
import com.ordersphere.events.PaymentInitiatedEvent;
import com.ordersphere.events.RefundIssuedEvent;
import com.ordersphere.payment.domain.Payment;
import com.ordersphere.payment.domain.PaymentMethod;
import com.ordersphere.payment.domain.PaymentStatus;
import com.ordersphere.payment.domain.Refund;
import com.ordersphere.payment.domain.RefundStatus;
import com.ordersphere.payment.dto.CreatePaymentRequest;
import com.ordersphere.payment.dto.PaymentResponse;
import com.ordersphere.payment.dto.RefundRequest;
import com.ordersphere.payment.dto.RefundResponse;
import com.ordersphere.payment.exception.InvalidPaymentStateException;
import com.ordersphere.payment.exception.PaymentMethodNotFoundException;
import com.ordersphere.payment.exception.PaymentNotFoundException;
import com.ordersphere.payment.gateway.GatewayResult;
import com.ordersphere.payment.gateway.PaymentGatewayClient;
import com.ordersphere.payment.repository.PaymentMethodRepository;
import com.ordersphere.payment.repository.PaymentRepository;
import com.ordersphere.payment.repository.RefundRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final PaymentMethodRepository paymentMethodRepository;
  private final RefundRepository refundRepository;
  private final PaymentGatewayClient gatewayClient;
  private final ApplicationEventPublisher eventPublisher;
  private final int maxRetries;

  public PaymentService(
      PaymentRepository paymentRepository,
      PaymentMethodRepository paymentMethodRepository,
      RefundRepository refundRepository,
      PaymentGatewayClient gatewayClient,
      ApplicationEventPublisher eventPublisher,
      @Value("${payment.processing.max-retries}") int maxRetries) {
    this.paymentRepository = paymentRepository;
    this.paymentMethodRepository = paymentMethodRepository;
    this.refundRepository = refundRepository;
    this.gatewayClient = gatewayClient;
    this.eventPublisher = eventPublisher;
    this.maxRetries = maxRetries;
  }

  @Transactional
  public PaymentResponse initiatePayment(String username, CreatePaymentRequest request) {
    Optional<Payment> existing = paymentRepository.findByOrderId(request.orderId());
    if (existing.isPresent()) {
      return PaymentResponse.from(existing.get());
    }

    PaymentMethod paymentMethod =
        paymentMethodRepository
            .findByIdAndCustomerUsername(request.paymentMethodId(), username)
            .orElseThrow(() -> new PaymentMethodNotFoundException(request.paymentMethodId()));

    Payment payment =
        new Payment(
            request.orderId(), username, paymentMethod, request.amount(), request.currency());
    paymentRepository.save(payment);

    eventPublisher.publishEvent(
        new PaymentInitiatedEvent(
            payment.getId(), payment.getOrderId(), payment.getAmount(), payment.getCurrency()));

    return PaymentResponse.from(payment);
  }

  @Transactional(readOnly = true)
  public PaymentResponse getPayment(String username, boolean isAdmin, Long paymentId) {
    return PaymentResponse.from(findPaymentOrThrow(username, isAdmin, paymentId));
  }

  @Transactional
  public RefundResponse refundPayment(
      String username, boolean isAdmin, Long paymentId, RefundRequest request) {
    Payment payment = findPaymentOrThrow(username, isAdmin, paymentId);

    Optional<Refund> existing = refundRepository.findByPaymentId(paymentId);
    if (existing.isPresent()) {
      return RefundResponse.from(existing.get());
    }

    if (payment.getStatus() != PaymentStatus.COMPLETED) {
      throw new InvalidPaymentStateException(
          "Cannot refund payment " + paymentId + " in status " + payment.getStatus());
    }

    Refund refund = new Refund(payment, payment.getAmount(), request.reason());
    refundRepository.save(refund);

    return RefundResponse.from(refund);
  }

  @Transactional
  public void settlePayment(Payment payment) {
    GatewayResult result =
        gatewayClient.authorize(
            payment.getPaymentMethod().getToken(), payment.getAmount(), payment.getCurrency());

    switch (result.outcome()) {
      case SUCCESS -> {
        payment.setGatewayReference(result.reference());
        payment.markStatus(PaymentStatus.COMPLETED);
        paymentRepository.save(payment);
        eventPublisher.publishEvent(
            new PaymentCompletedEvent(
                payment.getId(), payment.getOrderId(), payment.getCustomerUsername()));
      }
      case DECLINED -> failPayment(payment, "Payment declined by gateway");
      case TRANSIENT_FAILURE -> {
        payment.setRetryCount(payment.getRetryCount() + 1);
        if (payment.getRetryCount() >= maxRetries) {
          failPayment(payment, "Payment failed after " + maxRetries + " retries");
        } else {
          paymentRepository.save(payment);
        }
      }
    }
  }

  @Transactional
  public void settleRefund(Refund refund) {
    Payment payment = refund.getPayment();
    GatewayResult result =
        gatewayClient.refund(payment.getPaymentMethod().getToken(), refund.getAmount());

    if (result.outcome() == GatewayResult.GatewayOutcome.SUCCESS) {
      refund.setGatewayReference(result.reference());
      refund.setStatus(RefundStatus.COMPLETED);
      refundRepository.save(refund);
      payment.markStatus(PaymentStatus.REFUNDED);
      paymentRepository.save(payment);
      eventPublisher.publishEvent(
          new RefundIssuedEvent(
              payment.getId(), refund.getId(), payment.getOrderId(), refund.getAmount()));
    } else {
      refund.setStatus(RefundStatus.FAILED);
      refundRepository.save(refund);
    }
  }

  private void failPayment(Payment payment, String reason) {
    payment.markStatus(PaymentStatus.FAILED);
    paymentRepository.save(payment);
    eventPublisher.publishEvent(
        new PaymentFailedEvent(
            payment.getId(), payment.getOrderId(), reason, payment.getCustomerUsername()));
  }

  private Payment findPaymentOrThrow(String username, boolean isAdmin, Long paymentId) {
    if (isAdmin) {
      return paymentRepository
          .findById(paymentId)
          .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
    return paymentRepository
        .findByIdAndCustomerUsername(paymentId, username)
        .orElseThrow(() -> new PaymentNotFoundException(paymentId));
  }
}
