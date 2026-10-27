package dev.strategos.core.testkit;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** Real server tick spacing for asynchronous performance fixtures, including the first interval. */
public final class StepPacer {
    private final long interval;
    private final LongSupplier clock;
    private final LongConsumer wait;
    private long next;

    public StepPacer(long interval) {
        this(interval, System::nanoTime, StepPacer::sleep);
    }

    StepPacer(long interval, LongSupplier clock, LongConsumer wait) {
        if (interval <= 0) {
            throw new IllegalArgumentException("a positive tick interval is required");
        }
        this.interval = interval;
        this.clock = clock;
        this.wait = wait;
        next = clock.getAsLong() + interval;
    }

    /** Waits until the next tick may start; a slow tick never causes a catch-up burst. */
    public void awaitTick() {
        long left;
        while ((left = next - clock.getAsLong()) > 0) {
            wait.accept(left);
        }
        next = clock.getAsLong() + interval;
    }

    private static void sleep(long nanos) {
        try {
            Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("tick pacing interrupted", e);
        }
    }
}
