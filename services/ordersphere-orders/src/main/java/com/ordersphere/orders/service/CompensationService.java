package com.ordersphere.orders.service;

import com.ordersphere.orders.client.InventoryClient;
import com.ordersphere.orders.client.PaymentClient;
import com.ordersphere.orders.client.ServiceTokenProvider;
import com.ordersphere.orders.domain.Compensation;
import com.ordersphere.orders.dto.CompensationResponse;
import com.ordersphere.orders.exception.CompensationCallException;
import com.ordersphere.orders.exception.CompensationNotFoundException;
import com.ordersphere.orders.exception.CompensationNotRetryableException;
import com.ordersphere.orders.repository.CompensationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refunds and stock releases the saga owes other services, as a durable outbox. A compensation is
 * recorded in the same transaction as the order change that needs it (so it can't be lost), tried
 * right after that transaction commits (so the usual case is immediate), and otherwise retried by
 * the saga sweep with exponential back-off. Both remote operations are idempotent, so a retry after
 * an ambiguous failure can't refund twice.
 */
@Service
public class CompensationService {

  private static final Logger log = LoggerFactory.getLogger(CompensationService.class);
  private static final int MAX_ERROR_LENGTH = 1000;

  private final CompensationRepository repository;
  private final InventoryClient inventoryClient;
  private final PaymentClient paymentClient;
  private final ServiceTokenProvider serviceTokenProvider;
  private final TransactionTemplate newTransaction;
  private final int maxAttempts;
  private final Duration initialBackoff;
  private final Duration maxBackoff;
  private final Clock clock;

  @Autowired
  public CompensationService(
      CompensationRepository repository,
      InventoryClient inventoryClient,
      PaymentClient paymentClient,
      ServiceTokenProvider serviceTokenProvider,
      PlatformTransactionManager transactionManager,
      @Value("${orders.compensation.max-attempts:20}") int maxAttempts,
      @Value("${orders.compensation.initial-backoff:PT5S}") Duration initialBackoff,
      @Value("${orders.compensation.max-backoff:PT10M}") Duration maxBackoff) {
    this(
        repository,
        inventoryClient,
        paymentClient,
        serviceTokenProvider,
        transactionManager,
        maxAttempts,
        initialBackoff,
        maxBackoff,
        Clock.systemUTC());
  }

  CompensationService(
      CompensationRepository repository,
      InventoryClient inventoryClient,
      PaymentClient paymentClient,
      ServiceTokenProvider serviceTokenProvider,
      PlatformTransactionManager transactionManager,
      int maxAttempts,
      Duration initialBackoff,
      Duration maxBackoff,
      Clock clock) {
    this.repository = repository;
    this.inventoryClient = inventoryClient;
    this.paymentClient = paymentClient;
    this.serviceTokenProvider = serviceTokenProvider;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.maxAttempts = maxAttempts;
    this.initialBackoff = initialBackoff;
    this.maxBackoff = maxBackoff;
    this.clock = clock;
  }

  /** Records that the order's stock reservation must be released. */
  public void releaseInventory(Long orderId) {
    enqueue(new Compensation(Compensation.Type.RELEASE_INVENTORY, orderId, null, null, now()));
  }

  /** Records that the order's payment must be refunded. */
  public void refundPayment(Long orderId, Long paymentId, String reason) {
    enqueue(new Compensation(Compensation.Type.REFUND_PAYMENT, orderId, paymentId, reason, now()));
  }

  /** Admin view: compensations in the given state, most recently changed first. */
  @Transactional(readOnly = true)
  public List<CompensationResponse> list(Compensation.Status status) {
    return repository.findByStatusOrderByUpdatedAtDesc(status).stream()
        .map(CompensationResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public CompensationResponse get(Long id) {
    return repository
        .findById(id)
        .map(CompensationResponse::from)
        .orElseThrow(() -> new CompensationNotFoundException(id));
  }

  /**
   * Admin action once whatever made a compensation fail is fixed: puts a FAILED one back in the
   * queue with a fresh retry budget and attempts it right after this transaction commits.
   */
  @Transactional
  public void retry(Long id) {
    Compensation compensation =
        repository.findById(id).orElseThrow(() -> new CompensationNotFoundException(id));
    if (compensation.getStatus() != Compensation.Status.FAILED) {
      throw new CompensationNotRetryableException(id, compensation.getStatus());
    }
    compensation.setStatus(Compensation.Status.PENDING);
    compensation.setAttempts(0);
    compensation.setNextAttemptAt(now());
    compensation.setUpdatedAt(now());
    repository.save(compensation);
    log.info(
        "{} for orderId {} re-queued by an admin",
        compensation.getType(),
        compensation.getOrderId());
    attemptAfterCommit(id);
  }

  /** Saga sweep: attempts every compensation that is due. */
  public void processDue() {
    for (Long id : repository.findDueIds(now())) {
      attempt(id);
    }
  }

  private void enqueue(Compensation compensation) {
    attemptAfterCommit(repository.save(compensation).getId());
  }

  private void attemptAfterCommit(Long id) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      // Only once the order change is committed - otherwise a rolled-back cancellation could
      // still release stock or refund.
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              attempt(id);
            }
          });
    }
  }

  /** One attempt, in its own transaction; never throws. */
  void attempt(Long id) {
    try {
      newTransaction.executeWithoutResult(status -> repository.claim(id).ifPresent(this::run));
    } catch (RuntimeException ex) {
      log.warn("Compensation {} attempt errored, will be retried: {}", id, ex.getMessage());
    }
  }

  private void run(Compensation compensation) {
    compensation.setAttempts(compensation.getAttempts() + 1);
    compensation.setUpdatedAt(now());
    try {
      execute(compensation);
      compensation.setStatus(Compensation.Status.DONE);
      compensation.setLastError(null);
    } catch (CompensationCallException ex) {
      if (isAlreadySatisfied(compensation, ex)) {
        compensation.setStatus(Compensation.Status.DONE);
      } else {
        recordFailure(compensation, ex.getMessage(), ex.isRetryable());
      }
    } catch (ServiceTokenProvider.ServiceTokenException ex) {
      recordFailure(compensation, ex.getMessage(), true);
    }
    repository.save(compensation);
  }

  private void execute(Compensation compensation) {
    String token = serviceTokenProvider.bearerToken();
    switch (compensation.getType()) {
      case RELEASE_INVENTORY -> inventoryClient.release(compensation.getOrderId(), token);
      case REFUND_PAYMENT ->
          paymentClient.refund(compensation.getPaymentId(), compensation.getReason(), token);
    }
  }

  /** No reservation to release (never made, or already gone) means there's nothing left to do. */
  private static boolean isAlreadySatisfied(
      Compensation compensation, CompensationCallException ex) {
    return compensation.getType() == Compensation.Type.RELEASE_INVENTORY
        && Integer.valueOf(404).equals(ex.getStatus());
  }

  private void recordFailure(Compensation compensation, String error, boolean retryable) {
    compensation.setLastError(truncate(error));
    if (!retryable || compensation.getAttempts() >= maxAttempts) {
      compensation.setStatus(Compensation.Status.FAILED);
      log.error(
          "Giving up on {} for orderId {} after {} attempt(s) - needs manual attention: {}",
          compensation.getType(),
          compensation.getOrderId(),
          compensation.getAttempts(),
          error);
      return;
    }
    Duration backoff = backoff(compensation.getAttempts());
    compensation.setNextAttemptAt(now().plus(backoff));
    log.warn(
        "{} for orderId {} failed (attempt {}), retrying in {}s: {}",
        compensation.getType(),
        compensation.getOrderId(),
        compensation.getAttempts(),
        backoff.toSeconds(),
        error);
  }

  /** initial, 2x, 4x, ... capped at maxBackoff. */
  Duration backoff(int attempts) {
    Duration backoff = initialBackoff.multipliedBy(1L << Math.min(attempts - 1, 20));
    return backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff;
  }

  private Instant now() {
    return clock.instant();
  }

  private static String truncate(String error) {
    if (error == null || error.length() <= MAX_ERROR_LENGTH) {
      return error;
    }
    return error.substring(0, MAX_ERROR_LENGTH);
  }
}
