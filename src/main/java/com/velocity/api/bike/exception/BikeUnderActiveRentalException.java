package com.velocity.api.bike.exception;

import com.velocity.api.reservation.dto.ConflictDto;
import lombok.Getter;

import java.util.List;

@Getter
public class BikeUnderActiveRentalException extends RuntimeException {
    private final List<ConflictDto> conflicts;

    public BikeUnderActiveRentalException(List<ConflictDto> conflicts) {
        super("Cannot change bike status:" + conflicts.size() + " active reservations found.");
        this.conflicts = conflicts;
    }
}
