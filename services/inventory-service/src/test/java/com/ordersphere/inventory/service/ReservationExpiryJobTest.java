package com.ordersphere.inventory.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ordersphere.inventory.domain.Reservation;
import com.ordersphere.inventory.domain.ReservationStatus;
import com.ordersphere.inventory.repository.ReservationRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationExpiryJobTest {

  @Mock private ReservationRepository reservationRepository;
  @Mock private ReservationService reservationService;

  @Test
  void sweepExpiresEachOverdueActiveReservation() {
    Reservation overdue = new Reservation(1L, Instant.now().minusSeconds(1));
    when(reservationRepository.findByStatusAndExpiresAtBefore(eq(ReservationStatus.ACTIVE), any()))
        .thenReturn(List.of(overdue));

    ReservationExpiryJob job = new ReservationExpiryJob(reservationRepository, reservationService);
    job.sweepExpiredReservations();

    verify(reservationService).expire(overdue);
  }

  @Test
  void sweepDoesNothingWhenNoReservationsAreOverdue() {
    when(reservationRepository.findByStatusAndExpiresAtBefore(eq(ReservationStatus.ACTIVE), any()))
        .thenReturn(List.of());

    ReservationExpiryJob job = new ReservationExpiryJob(reservationRepository, reservationService);
    job.sweepExpiredReservations();

    verifyNoInteractions(reservationService);
  }
}
