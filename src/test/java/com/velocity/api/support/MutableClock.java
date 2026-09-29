package com.velocity.api.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock tests can stop and move. It follows real time until a test fixes it, and
 * {@link #reset()} hands it back to real time.
 *
 * <p>One shared instance replaces per-class {@code @MockitoBean Clock} fields: every distinct
 * mock set is a distinct Spring context, and the suite used to start six of them.
 */
public class MutableClock extends Clock {
    private final ZoneId zone;
    private volatile Instant fixedInstant;

    public MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        this.fixedInstant = instant;
    }

    public void setInstant(String isoInstant) {
        setInstant(Instant.parse(isoInstant));
    }

    public void reset() {
        this.fixedInstant = null;
    }

    @Override
    public Instant instant() {
        Instant fixed = fixedInstant;
        return fixed != null ? fixed : Instant.now();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return zone.equals(this.zone) ? this : Clock.fixed(instant(), zone);
    }
}
