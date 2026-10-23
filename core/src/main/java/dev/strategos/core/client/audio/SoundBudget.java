package dev.strategos.core.client.audio;

import dev.strategos.core.protocol.FxKind;
import java.util.EnumMap;

/**
 * How many sounds of a kind may start in a client tick (AUD-01): a melee's hits past {@link #PER_TICK} add noise, not
 * information, so they are dropped.
 */
public final class SoundBudget {
    public static final int PER_TICK = 6;

    private final EnumMap<FxKind, Integer> started = new EnumMap<>(FxKind.class);
    private long tick = Long.MIN_VALUE;

    /** Whether a sound of {@code kind} may start in {@code clientTick}; counts it if so. */
    public boolean take(FxKind kind, long clientTick) {
        if (clientTick != tick) {
            started.clear();
            tick = clientTick;
        }
        int n = started.getOrDefault(kind, 0);
        if (n >= PER_TICK) {
            return false;
        }
        started.put(kind, n + 1);
        return true;
    }
}
