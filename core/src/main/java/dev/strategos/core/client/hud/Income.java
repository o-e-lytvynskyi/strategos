package dev.strategos.core.client.hud;

import dev.strategos.core.protocol.FactionState;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Income per minute (UI-07), from the {@code gathered} totals of the faction states the client receives: per resource,
 * what was gathered over the last minute of match ticks, measured from the newest state back to the oldest kept that
 * is at least a minute older; with less than a minute seen, the rate so far scaled to a minute. A state of another
 * match, or an older tick, starts afresh.
 */
public final class Income {
    /** A minute of match ticks. */
    public static final int MINUTE = 1200;

    private record Sample(int matchId, long tick, List<Long> gathered) {}

    private final Deque<Sample> samples = new ArrayDeque<>();

    /** Takes a faction state. */
    public void accept(FactionState state) {
        Sample last = samples.peekLast();
        if (last != null && (last.matchId() != state.matchId() || state.tick() < last.tick())) {
            samples.clear();
        }
        if (last != null && last.matchId() == state.matchId() && state.tick() == last.tick()) {
            samples.pollLast();
        }
        samples.addLast(new Sample(state.matchId(), state.tick(), state.gathered()));
        // keep one sample at least a minute old, the base of the window
        while (samples.size() > 2) {
            var it = samples.iterator();
            it.next();
            Sample second = it.next();
            if (state.tick() - second.tick() >= MINUTE) {
                samples.pollFirst();
            } else {
                break;
            }
        }
    }

    /** Resource {@code r}'s income per minute, in thousandths; 0 before two states. */
    public long perMinute(int r) {
        Sample newest = samples.peekLast();
        Sample oldest = samples.peekFirst();
        if (newest == null
                || oldest == null
                || samples.size() < 2
                || r >= newest.gathered().size()) {
            return 0;
        }
        long base = r < oldest.gathered().size() ? oldest.gathered().get(r) : 0;
        long span = newest.tick() - oldest.tick();
        long gathered = newest.gathered().get(r) - base;
        return gathered * MINUTE / Math.max(1, span);
    }

    public void clear() {
        samples.clear();
    }
}
