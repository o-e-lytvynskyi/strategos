package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.Limits;

/** Accepted inclusive command ranges for one authenticated recipient view; gaps are permitted. */
final class CommandSequenceWindow {
    private long through = -1;

    boolean take(int first, int count) {
        if (first < 0 || count < 1 || count > Limits.BATCH_COMMANDS) return false;
        long last = (long) first + count - 1;
        if (first <= through || last > Integer.MAX_VALUE) return false;
        through = last;
        return true;
    }
}
