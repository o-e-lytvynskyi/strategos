package dev.strategos.core.port;

import dev.strategos.core.shared.CoreApi;

/**
 * Where a match's journal goes besides the core's memory (spec §5.10, CORE-11): how much of it the core keeps, and,
 * for a sink that {@link #writes()}, the whole kept journal in its binary form each time a segment closes, which the
 * mod writes to {@code <world>/strategos/journals/<match>.strj} ({@code journal = disk}).
 */
@CoreApi
public interface JournalSink {
    /** An hour of ticks: what the core keeps in memory by default. */
    int HOUR = 72_000;

    /** Memory only, the last hour ({@code journal = memory}, and every test). */
    JournalSink MEMORY = new JournalSink() {};

    /** Ticks of journal the core keeps; whole segments of 1,200 ticks. */
    default int keepTicks() {
        return HOUR;
    }

    /** Whether {@link #write} wants the journal; the core encodes nothing for a sink that does not. */
    default boolean writes() {
        return false;
    }

    /** The kept journal at {@code tick}, the end of a segment, in the binary journal format. */
    default void write(long tick, byte[] journal) {}
}
