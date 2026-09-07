package com.velocity.api.reservation.scheduler;

import com.velocity.api.reservation.ReservationStatus;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReservationLifecycleScheduler {
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    // @Scheduled(fixedRate = 60000)
    @Scheduled(fixedRate = 10000)
    public void cancelStalePendingReservations() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.MINUTES);
        List<UUID> stalePendingReservationIds = reservationRepository.findStalePendingReservationsIds(ReservationStatus.PENDING, cutoff);

        for (UUID id : stalePendingReservationIds) {
            try {
                reservationService.cancelStaleReservation(id);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Reservation {} already modified.", id);
            } catch (Exception e) {
                log.error("Unexpected error processing reservation {}", id, e);
            }
        }
    }

    // @Scheduled(cron = "0 1 0 * * *")
    @Scheduled(fixedRate = 10000) // for testing
    public void completePastDueConfirmedReservations() {

        List<UUID> pastDueConfirmedReservationIds = reservationRepository.findPastDueConfirmedReservationsIds(ReservationStatus.CONFIRMED, LocalDate.now());

        for (UUID id : pastDueConfirmedReservationIds) {
            try {
                reservationService.completePastDueConfirmedReservations(id);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("Reservation {} already modified.", id);
            } catch (Exception e) {
                log.error("Unexpected error processing reservation {}", id, e);
            }
        }
    }
}
