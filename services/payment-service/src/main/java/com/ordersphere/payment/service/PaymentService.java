package com.ordersphere.payment.service;

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
import com.ordersphere.payment.dto.RefundResponse;
import com.ordersphere.payment.exception.InvalidPaymentStateException;
import com.ordersphere.payment.exception.PaymentNotFoundException;
import com.ordersphere.payment.gateway.PaymentGatewayClient;
import com.ordersphere.payment.gateway.PaymentGatewayClient.CheckoutSession;
import com.ordersphere.payment.gateway.PaymentGatewayClient.SessionStatus;
import com.ordersphere.payment.gateway.PaymentGatewayException;
import com.ordersphere.payment.logging.PaymentLogContext;
import com.ordersphere.payment.repository.PaymentRepository;
import com.ordersphere.payment.repository.RefundRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payments are collected on the provider's hosted checkout page. Initiating a payment opens a
 * checkout session; the payment stays PENDING until the provider reports the session's outcome - by
 * webhook ({@link #applySessionOutcome}) or, if that is lost, by the sweep's session lookup ({@link
 * #reconcile}).
 */
@Service
public class PaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private final PaymentRepository paymentRepository;
  private final RefundRepository refundRepository;
  private final PaymentGatewayClient gatewayClient;
  private final ApplicationEventPublisher eventPublisher;
  private final String returnUrl;
  private final String webhookUrl;

  public PaymentService(
      PaymentRepository paymentRepository,
      RefundRepository refundRepository,
      PaymentGatewayClient gatewayClient,
      ApplicationEventPublisher eventPublisher,
      @Value("${payment.checkout.return-url}") String returnUrl,
      @Value("${payment.checkout.webhook-url}") String webhookUrl) {
    this.paymentRepository = paymentRepository;
    this.refundRepository = refundRepository;
    this.gatewayClient = gatewayClient;
    this.eventPublisher = eventPublisher;
    this.returnUrl = returnUrl;
    this.webhookUrl = webhookUrl;
  }

  /**
   * Idempotent per orderId: a retry with the same customer, amount and currency gets the existing
   * payment back; anything else is rejected, so a payment can't be pre-created for someone else's
   * (or a not-yet-placed) order at a different price. Throws {@link PaymentGatewayException}
   * (rolling the payment back) if the provider can't open a checkout session.
   */
  @Transactional
  public PaymentResponse initiatePayment(CreatePaymentRequest request) {
    Optional<Payment> existing = paymentRepository.findByOrderId(request.orderId());
    if (existing.isPresent()) {
      Payment payment = existing.get();
      if (!payment.getCustomerUsername().equals(request.customerUsername())
          || payment.getAmount().compareTo(request.amount()) != 0
          || !payment.getCurrency().equals(request.currency())) {
        throw new InvalidPaymentStateException(
            "A different payment already exists for orderId " + request.orderId());
      }
      return PaymentResponse.from(payment);
    }

    String username = request.customerUsername();
    Payment payment =
        new Payment(request.orderId(), username, request.amount(), request.currency());
    paymentRepository.save(payment);

    String orderQuery = (returnUrl.contains("?") ? "&" : "?") + "orderId=" + request.orderId();
    CheckoutSession session =
        gatewayClient.createCheckoutSession(
            new PaymentGatewayClient.CheckoutRequest(
                String.valueOf(payment.getId()),
                payment.getAmount(),
                payment.getCurrency(),
                "OrderSphere order #" + request.orderId(),
                returnUrl + orderQuery + "&payment=success",
                returnUrl + orderQuery + "&payment=cancelled",
                webhookUrl));
    payment.setCheckoutSessionId(session.id());
    payment.setCheckoutUrl(session.url());
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

  /** Only reachable by SERVICE/ADMIN callers (see PaymentController). */
  @Transactional
  public RefundResponse refundPayment(Long paymentId, RefundRequest request) {
    Payment payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));

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

  /**
   * Applies an outcome the provider pushed by webhook. Unknown sessions are ignored, and so is any
   * outcome for a payment that is no longer PENDING (webhook and sweep may both report it).
   */
  @Transactional
  public void applySessionOutcome(String sessionId, SessionStatus status, String chargeReference) {
    paymentRepository
        .findByCheckoutSessionIdForUpdate(sessionId)
        .ifPresentOrElse(
            payment -> {
              // Webhooks carry only the session id: name the payment and order for the logs.
              try (var logContext =
                  PaymentLogContext.forPayment(payment.getId(), payment.getOrderId())) {
                apply(payment, status, chargeReference);
              }
            },
            () -> log.warn("Ignoring gateway outcome for unknown checkout session {}", sessionId));
  }

  /** Polls the provider for a still-PENDING payment, in case its webhook never arrived. */
  @Transactional
  public void reconcile(Long paymentId) {
    Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
    if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
      return;
    }
    if (payment.getCheckoutSessionId() == null) {
      failPayment(payment, "No checkout session");
      return;
    }

    Optional<CheckoutSession> session;
    try {
      session = gatewayClient.getCheckoutSession(payment.getCheckoutSessionId());
    } catch (PaymentGatewayException ex) {
      log.warn("Could not reconcile paymentId {}: {}", paymentId, ex.getMessage());
      return;
    }
    if (session.isEmpty()) {
      failPayment(payment, "Checkout session not found at payment provider");
      return;
    }
    apply(payment, session.get().status(), session.get().chargeReference());
  }

  @Transactional
  public void settleRefund(Refund refund) {
    Payment payment = refund.getPayment();
    Optional<String> refundReference;
    try {
      refundReference = gatewayClient.refund(payment.getGatewayReference());
    } catch (PaymentGatewayException ex) {
      // Provider unavailable: leave the refund PENDING for the next sweep.
      log.warn("Refund {} not settled yet: {}", refund.getId(), ex.getMessage());
      return;
    }

    if (refundReference.isPresent()) {
      refund.setGatewayReference(refundReference.get());
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

  // --- Reconciliation re-sync: apply what the provider's records say, through the same paths.

  /**
   * The provider charged the card but our payment isn't COMPLETED (e.g. it was failed while the
   * provider was unreachable). Completes it, so the orders saga confirms the order - or refunds it
   * automatically if the order was cancelled meanwhile.
   */
  @Transactional
  public void recordProviderCharge(Long paymentId, String chargeReference) {
    Payment payment = lockedOrThrow(paymentId);
    if (payment.getStatus() == PaymentStatus.COMPLETED
        || payment.getStatus() == PaymentStatus.REFUNDED) {
      return;
    }
    log.warn(
        "Reconciliation: recording provider charge {} for paymentId {} (was {})",
        chargeReference,
        paymentId,
        payment.getStatus());
    payment.setFailureReason(null);
    completePayment(payment, chargeReference);
  }

  /** The provider refunded the charge but we don't show it: record the refund as done. */
  @Transactional
  public void recordProviderRefund(Long paymentId, String refundReference) {
    Payment payment = lockedOrThrow(paymentId);
    if (payment.getStatus() == PaymentStatus.REFUNDED) {
      return;
    }
    Refund refund =
        refundRepository
            .findByPaymentId(paymentId)
            .orElseGet(
                () ->
                    new Refund(
                        payment, payment.getAmount(), "Recorded from the provider's settlement"));
    refund.setGatewayReference(refundReference);
    refund.setStatus(RefundStatus.COMPLETED);
    refundRepository.save(refund);
    payment.markStatus(PaymentStatus.REFUNDED);
    paymentRepository.save(payment);
    eventPublisher.publishEvent(
        new RefundIssuedEvent(
            payment.getId(), refund.getId(), payment.getOrderId(), refund.getAmount()));
  }

  /**
   * We show the payment REFUNDED but the provider never refunded the charge: refund it at the
   * provider now (idempotent there). Returns the provider's refund reference.
   */
  @Transactional
  public String executeMissingRefund(Long paymentId) {
    Payment payment = lockedOrThrow(paymentId);
    String refundReference =
        gatewayClient
            .refund(payment.getGatewayReference())
            .orElseThrow(
                () ->
                    new InvalidPaymentStateException(
                        "The provider doesn't know charge " + payment.getGatewayReference()));
    refundRepository
        .findByPaymentId(paymentId)
        .ifPresent(
            refund -> {
              refund.setGatewayReference(refundReference);
              refund.setStatus(RefundStatus.COMPLETED);
              refundRepository.save(refund);
            });
    return refundReference;
  }

  private Payment lockedOrThrow(Long paymentId) {
    return paymentRepository
        .findByIdForUpdate(paymentId)
        .orElseThrow(() -> new PaymentNotFoundException(paymentId));
  }

  private void apply(Payment payment, SessionStatus status, String chargeReference) {
    if (payment.getStatus() != PaymentStatus.PENDING) {
      return;
    }
    switch (status) {
      case OPEN -> {}
      case SUCCEEDED -> completePayment(payment, chargeReference);
      case CANCELLED -> failPayment(payment, "Payment cancelled by customer");
      case EXPIRED -> failPayment(payment, "Payment window expired");
    }
  }

  private void completePayment(Payment payment, String chargeReference) {
    payment.setGatewayReference(chargeReference);
    payment.markStatus(PaymentStatus.COMPLETED);
    paymentRepository.save(payment);
    eventPublisher.publishEvent(
        new PaymentCompletedEvent(
            payment.getId(),
            payment.getOrderId(),
            payment.getCustomerUsername(),
            payment.getAmount(),
            payment.getCurrency()));
  }

  private void failPayment(Payment payment, String reason) {
    payment.setFailureReason(reason);
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
