package dev.strategos.mod.server.tick;

/**
 * Our work outside the server's measured tick, for budget C (04 §17.2): our {@code ServerTickEvent.Post} work, which
 * runs after the tick's time is stored (MinecraftServer.java:954, 958), and our payload handlers that ran between ticks,
 * as NeoForge's main-thread handlers do when the server works through its task queue. The tick driver opens each tick
 * and closes it with its post work; a handler outside a tick adds its time to the gap before the next. Server thread
 * only.
 */
public final class BetweenTicks {
    private boolean inTick;
    private long gap;
    private long last;
    private long total;

    /** A server tick starts: the gap before it closes. */
    public void tickStarts() {
        inTick = true;
        last = gap;
        total += gap;
        gap = 0;
    }

    /** A server tick ends with {@code postNanos} of our post work, the gap's first part. */
    public void tickEnds(long postNanos) {
        inTick = false;
        gap += postNanos;
    }

    /** One handler's time: outside a tick, part of the gap before the next. */
    public void handled(long nanos) {
        if (!inTick) {
            gap += nanos;
        }
    }

    /** Our work in the gap before the current tick, in nanoseconds. */
    public long last() {
        return last;
    }

    /** Our work outside measured ticks since the server started, in nanoseconds. */
    public long total() {
        return total;
    }
}
