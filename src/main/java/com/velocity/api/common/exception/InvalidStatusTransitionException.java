package com.velocity.api.common.exception;

import com.velocity.api.reservation.ReservationStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    // The constructor forces to provide the states
    public InvalidStatusTransitionException(ReservationStatus from, ReservationStatus to) {
        super(String.format("Invalid reservation state transition from %s to %s.", from, to));
    }
}