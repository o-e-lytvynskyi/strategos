package dev.strategos.mod.server.net;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a player may send (04 §12.6): token buckets of 40 commands a second with bursts of 80 and 3,000 handles a
 * second, refilled each server tick; and its violations (a batch that would not read, one over the rate, one from a
 * player with no seat) over the last minute, 1,200 ticks, of which the 101st disconnects it.
 */
public final class RateLimiter {
    /** Violations a player may have within a minute; the next one disconnects it. */
    public static final int VIOLATIONS_PER_MINUTE = 100;

    private static final int MINUTE = 1_200;
    private static final double COMMANDS_PER_TICK = 40 / 20.0;
    private static final double COMMAND_BURST = 80;
    private static final double HANDLES_PER_TICK = 3_000 / 20.0;
    private static final double HANDLE_BURST = 3_000;

    private final Map<UUID, Player> players = new HashMap<>();

    private static final class Player {
        double commands = COMMAND_BURST;
        double handles = HANDLE_BURST;
        long refilled;
        final ArrayDeque<Long> violations = new ArrayDeque<>();

        Player(long tick) {
            refilled = tick;
        }
    }

    /** Whether a batch of {@code commands} commands over {@code handles} handles fits now; if so, it is taken. */
    public boolean take(UUID player, long tick, int commands, int handles) {
        Player p = players.computeIfAbsent(player, k -> new Player(tick));
        long elapsed = Math.max(0, tick - p.refilled);
        p.commands = Math.min(COMMAND_BURST, p.commands + elapsed * COMMANDS_PER_TICK);
        p.handles = Math.min(HANDLE_BURST, p.handles + elapsed * HANDLES_PER_TICK);
        p.refilled = tick;
        if (p.commands < commands || p.handles < handles) {
            return false;
        }
        p.commands -= commands;
        p.handles -= handles;
        return true;
    }

    /** Counts one violation; true when it is one too many and the player must go. */
    public boolean violation(UUID player, long tick) {
        Player p = players.computeIfAbsent(player, k -> new Player(tick));
        while (!p.violations.isEmpty() && p.violations.peekFirst() <= tick - MINUTE) {
            p.violations.pollFirst();
        }
        p.violations.addLast(tick);
        return p.violations.size() > VIOLATIONS_PER_MINUTE;
    }

    /** A player who left starts afresh. */
    public void forget(UUID player) {
        players.remove(player);
    }
}
