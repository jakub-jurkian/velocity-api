package com.velocity.api.reservation.exception;

/**
 * A PENDING reservation whose confirmation window has passed. It can no longer be confirmed;
 * the customer has to book again.
 */
public class ReservationExpiredException extends RuntimeException {
    public ReservationExpiredException(String message) {
        super(message);
    }
}
