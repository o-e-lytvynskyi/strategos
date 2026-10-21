package dev.strategos.core.testkit;

import dev.strategos.core.port.Clock;

/** A clock that never moves. */
public final class FixedClock implements Clock {
    private final long nanos;

    public FixedClock(long nanos) {
        this.nanos = nanos;
    }

    @Override
    public long nanoTime() {
        return nanos;
    }
}
