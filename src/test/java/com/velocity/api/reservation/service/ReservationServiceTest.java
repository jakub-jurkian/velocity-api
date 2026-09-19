package com.velocity.api.reservation.service;

import com.velocity.api.reservation.exception.InvalidStatusTransitionException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {
    @Mock
    private ReservationRepository reservationRepository;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-09-01T10:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private ReservationService reservationService;


    @Test
    @DisplayName("When reservation ID does not exist, transition throws ResourceNotFoundException")
    public void transition_fakeID_throwsResourceNotFoundException() {
        UUID fakeId = UUID.randomUUID();
        when(reservationRepository.findById(fakeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reservationService.cancelStaleReservation(fakeId));
    }

    @Test
    @DisplayName("When transition is illegal, throws exception and does not save to database")
    public void transition_illegalState_neverSaves() {
        UUID validId = UUID.randomUUID();
        Reservation completedReservation = createWithStatus(ReservationStatus.COMPLETED);

        when(reservationRepository.findById(validId)).thenReturn(Optional.of(completedReservation));
        assertThrows(InvalidStatusTransitionException.class, () -> reservationService.cancelStaleReservation(validId));

        assertThat(completedReservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
    }
}
