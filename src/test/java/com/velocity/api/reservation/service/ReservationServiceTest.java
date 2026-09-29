package com.velocity.api.reservation.service;

import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.reservation.Reservation;
import com.velocity.api.reservation.ReservationStatus;
import com.velocity.api.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.velocity.api.reservation.ReservationTestFactory.createWithStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {
    @Mock
    private ReservationRepository reservationRepository;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private ReservationService reservationService;


    @Test
    @DisplayName("When reservation ID does not exist, expiring it throws ResourceNotFoundException")
    public void expire_fakeID_throwsResourceNotFoundException() {
        UUID fakeId = UUID.randomUUID();
        when(reservationRepository.findById(fakeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reservationService.expireStaleReservation(fakeId));
    }

    @Test
    @DisplayName("A reservation that stopped being PENDING before the sweep reached it is left alone")
    public void expire_noLongerPending_leavesReservationUnchanged() {
        UUID validId = UUID.randomUUID();
        Reservation confirmedReservation = createWithStatus(ReservationStatus.CONFIRMED);
        when(reservationRepository.findById(validId)).thenReturn(Optional.of(confirmedReservation));

        reservationService.expireStaleReservation(validId);

        assertThat(confirmedReservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmedReservation.getCancellationReason()).isNull();
    }
}
