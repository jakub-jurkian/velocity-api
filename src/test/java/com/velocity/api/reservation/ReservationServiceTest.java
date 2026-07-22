package com.velocity.api.reservation;

import com.velocity.api.common.exception.InvalidStatusTransitionException;
import com.velocity.api.common.exception.ResourceNotFoundException;
import com.velocity.api.reservation.repository.ReservationRepository;
import com.velocity.api.reservation.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ReservationServiceTest {
    @Mock
    private ReservationRepository reservationRepository;
    @InjectMocks
    private ReservationService reservationService;

    @Test
    @DisplayName("When reservation ID does not exist, transition throws ResourceNotFoundException")
    public void transition_fakeID_throwsResourceNotFoundException() {
        UUID fakeId = UUID.randomUUID();
        when(reservationRepository.findById(fakeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            reservationService.transition(fakeId, ReservationStatus.CONFIRMED);
        });
    }

    @Test
    @DisplayName("When transition is illegal, throws exception and does not save to database")
    public void transition_illegalState_neverSaves() {
        UUID validId = UUID.randomUUID();
        Reservation pendingReservation = new Reservation();
        pendingReservation.setStatusForTest(ReservationStatus.PENDING);

        when(reservationRepository.findById(validId)).thenReturn(Optional.of(pendingReservation));
        assertThrows(InvalidStatusTransitionException.class, () -> {
            reservationService.transition(validId, ReservationStatus.COMPLETED);
        });

        verify(reservationRepository, never()).save(any(Reservation.class));
    }
}
