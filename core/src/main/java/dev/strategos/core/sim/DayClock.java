package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * The match's day (MATCH-16): a pure function of the match tick, so there is nothing to save and a restored or replayed
 * match reads the same time. A day is {@link #CYCLE} ticks (20 minutes at 20 ticks a second) in vanilla's own units; the
 * match starts at {@link #START} (noon), so the first night falls {@code NIGHT_FROM - START} ticks in. The phases follow
 * the sky: day until {@link #DUSK_FROM}, dusk to {@link #NIGHT_FROM}, night to {@link #DAWN_FROM}, dawn to the next day.
 * Only the night changes vision; dusk and dawn are the sky's (the arena time, the client tint).
 */
@CoreApi
public final class DayClock {
    /** The length of a day in ticks: vanilla's, so the arena's time and the clock agree. */
    public static final int CYCLE = 24_000;

    /** Where the clock stands at match tick 0: noon, the arena's {@code NOON}. */
    public static final int START = 6_000;

    public static final int DUSK_FROM = 12_000;
    public static final int NIGHT_FROM = 13_000;
    public static final int DAWN_FROM = 23_000;

    /** A day's four phases. */
    @CoreApi
    public enum DayPhase {
        DAY,
        DUSK,
        NIGHT,
        DAWN
    }

    private DayClock() {}

    /** The time of day at match tick {@code tick}, in {@code [0, CYCLE)}: 0 is sunrise, 6000 noon, 18000 midnight. */
    public static int timeOfDay(long tick) {
        return (int) ((tick + START) % CYCLE);
    }

    public static DayPhase phase(long tick) {
        return phaseAt(timeOfDay(tick));
    }

    /** The phase of a time of day in {@code [0, CYCLE)}. */
    public static DayPhase phaseAt(int timeOfDay) {
        if (timeOfDay < DUSK_FROM) {
            return DayPhase.DAY;
        }
        if (timeOfDay < NIGHT_FROM) {
            return DayPhase.DUSK;
        }
        return timeOfDay < DAWN_FROM ? DayPhase.NIGHT : DayPhase.DAWN;
    }

    /** Whether it is night at match tick {@code tick}: the only phase that shrinks vision. */
    public static boolean night(long tick) {
        int t = timeOfDay(tick);
        return t >= NIGHT_FROM && t < DAWN_FROM;
    }

    /**
     * How dark the sky is at match tick {@code tick}, 0 to 1: 0 by day, rising through dusk to 1 at night's start, 1
     * all night, falling through dawn. What the client's tint draws when the world's own time is not the match's.
     */
    public static double darkness(long tick) {
        int t = timeOfDay(tick);
        if (t < DUSK_FROM) {
            return 0;
        }
        if (t < NIGHT_FROM) {
            return (t - DUSK_FROM) / (double) (NIGHT_FROM - DUSK_FROM);
        }
        return t < DAWN_FROM ? 1 : 1 - (t - DAWN_FROM) / (double) (CYCLE - DAWN_FROM);
    }

    /** How many ticks after {@code tick} the phase changes next ({@code 1} when it changes on the next tick). */
    public static int ticksToChange(long tick) {
        int t = timeOfDay(tick);
        int next = t < DUSK_FROM ? DUSK_FROM : t < NIGHT_FROM ? NIGHT_FROM : t < DAWN_FROM ? DAWN_FROM : CYCLE;
        return next - t;
    }
}
