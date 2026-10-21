package dev.strategos.core.testkit;

import dev.strategos.core.port.Clock;

/** A clock a test advances by hand. */
public final class ManualClock implements Clock {
    private long now;

    public void advance(long nanos) {
        now += nanos;
    }

    @Override
    public long nanoTime() {
        return now;
    }
}
