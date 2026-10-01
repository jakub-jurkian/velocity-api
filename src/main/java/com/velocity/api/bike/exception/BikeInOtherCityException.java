package com.velocity.api.bike.exception;

// A client tried to book a bike outside their own city. Availability only ever offers bikes
// in the caller's city, so this is a request built by hand.
public class BikeInOtherCityException extends RuntimeException {
    public BikeInOtherCityException(String message) {
        super(message);
    }
}
