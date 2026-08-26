package com.velocity.api.reservation;

import org.springframework.test.util.ReflectionTestUtils;

public class ReservationTestFactory {
    public static Reservation createWithStatus(ReservationStatus status) {
        Reservation reservation = new Reservation();
        ReflectionTestUtils.setField(reservation, "status", status);
        return reservation;
    }
}
