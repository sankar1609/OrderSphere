package com.ordersphere.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.orders.client.InventoryClient;
import com.ordersphere.orders.client.PaymentClient;
import com.ordersphere.orders.client.ServiceTokenProvider;
import com.ordersphere.orders.domain.Compensation;
import com.ordersphere.orders.exception.CompensationCallException;
import com.ordersphere.orders.repository.CompensationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class CompensationServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Mock private CompensationRepository repository;
  @Mock private InventoryClient inventoryClient;
  @Mock private PaymentClient paymentClient;
  @Mock private ServiceTokenProvider serviceTokenProvider;

  private CompensationService service;

  @BeforeEach
  void setUp() {
    service =
        new CompensationService(
            repository,
            inventoryClient,
            paymentClient,
            serviceTokenProvider,
            mock(PlatformTransactionManager.class),
            3,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            Clock.fixed(NOW, ZoneOffset.UTC));
    lenient().when(serviceTokenProvider.bearerToken()).thenReturn("Bearer service");
    lenient().when(repository.save(any(Compensation.class))).thenAnswer(i -> i.getArgument(0));
  }

  private Compensation refund() {
    Compensation compensation =
        new Compensation(Compensation.Type.REFUND_PAYMENT, 10L, 42L, "cancelled", NOW);
    compensation.setId(1L);
    when(repository.findDueIds(NOW)).thenReturn(List.of(1L));
    when(repository.claim(1L)).thenReturn(Optional.of(compensation));
    return compensation;
  }

  private static CompensationCallException failure(boolean retryable, Integer status) {
    return new CompensationCallException("boom", null, retryable, status);
  }

  @Test
  void successfulAttemptIsDone() {
    Compensation compensation = refund();

    service.processDue();

    verify(paymentClient).refund(42L, "cancelled", "Bearer service");
    assertThat(compensation.getStatus()).isEqualTo(Compensation.Status.DONE);
    assertThat(compensation.getAttempts()).isEqualTo(1);
  }

  @Test
  void retryableFailureIsRescheduledWithGrowingBackoff() {
    Compensation compensation = refund();
    doThrow(failure(true, 503)).when(paymentClient).refund(any(), any(), any());

    service.processDue();

    assertThat(compensation.getStatus()).isEqualTo(Compensation.Status.PENDING);
    assertThat(compensation.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
    assertThat(compensation.getLastError()).isEqualTo("boom");

    service.processDue();

    assertThat(compensation.getAttempts()).isEqualTo(2);
    assertThat(compensation.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
  }

  @Test
  void givesUpAfterMaxAttempts() {
    Compensation compensation = refund();
    doThrow(failure(true, 503)).when(paymentClient).refund(any(), any(), any());

    service.processDue();
    service.processDue();
    service.processDue();

    assertThat(compensation.getAttempts()).isEqualTo(3);
    assertThat(compensation.getStatus()).isEqualTo(Compensation.Status.FAILED);
  }

  @Test
  void rejectedCallFailsImmediately() {
    Compensation compensation = refund();
    doThrow(failure(false, 409)).when(paymentClient).refund(any(), any(), any());

    service.processDue();

    assertThat(compensation.getStatus()).isEqualTo(Compensation.Status.FAILED);
    assertThat(compensation.getAttempts()).isEqualTo(1);
  }

  @Test
  void unreachableAuthServiceIsRetried() {
    Compensation compensation = refund();
    when(serviceTokenProvider.bearerToken())
        .thenThrow(new ServiceTokenProvider.ServiceTokenException("auth down", null));

    service.processDue();

    assertThat(compensation.getStatus()).isEqualTo(Compensation.Status.PENDING);
    verify(paymentClient, never()).refund(any(), any(), any());
  }

  @Test
  void releasingAReservationThatNoLongerExistsCountsAsDone() {
    Compensation compensation =
        new Compensation(Compensation.Type.RELEASE_INVENTORY, 10L, null, null, NOW);
    compensation.setId(2L);
    when(repository.findDueIds(NOW)).thenReturn(List.of(2L));
    when(repository.claim(2L)).thenReturn(Optional.of(compensation));
    doThrow(failure(false, 404)).when(inventoryClient).release(10L, "Bearer service");

    service.processDue();

    assertThat(compensation.getStatus()).isEqualTo(Compensation.Status.DONE);
  }

  @Test
  void aCompensationAnotherThreadIsWorkingOnIsSkipped() {
    when(repository.findDueIds(NOW)).thenReturn(List.of(1L));
    when(repository.claim(1L)).thenReturn(Optional.empty());

    service.processDue();

    verify(paymentClient, never()).refund(any(), any(), any());
  }

  @Test
  void backoffDoublesUpToTheCap() {
    assertThat(service.backoff(1)).isEqualTo(Duration.ofSeconds(5));
    assertThat(service.backoff(2)).isEqualTo(Duration.ofSeconds(10));
    assertThat(service.backoff(3)).isEqualTo(Duration.ofSeconds(20));
    assertThat(service.backoff(4)).isEqualTo(Duration.ofSeconds(30));
    assertThat(service.backoff(40)).isEqualTo(Duration.ofSeconds(30));
  }
}
