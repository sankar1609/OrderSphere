package com.ordersphere.inventory.service;

import com.ordersphere.inventory.domain.Reservation;
import com.ordersphere.inventory.domain.ReservationStatus;
import com.ordersphere.inventory.repository.ReservationRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReservationExpiryJob {

  private final ReservationRepository reservationRepository;
  private final ReservationService reservationService;

  public ReservationExpiryJob(
      ReservationRepository reservationRepository, ReservationService reservationService) {
    this.reservationRepository = reservationRepository;
    this.reservationService = reservationService;
  }

  @Scheduled(fixedDelayString = "${inventory.reservation.expiry-sweep-interval-ms}")
  @Transactional
  public void sweepExpiredReservations() {
    for (Reservation reservation :
        reservationRepository.findByStatusAndExpiresAtBefore(
            ReservationStatus.ACTIVE, Instant.now())) {
      reservationService.expire(reservation);
    }
  }
}
