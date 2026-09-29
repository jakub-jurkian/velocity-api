package com.velocity.api.reservation.exception;

import com.velocity.api.reservation.ReservationStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(ReservationStatus from, ReservationStatus to) {
        super(String.format("Invalid reservation state transition from %s to %s.", from, to));
    }
}