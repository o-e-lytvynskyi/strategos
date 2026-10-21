package dev.strategos.core.testkit;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Rng;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchBounds;
import dev.strategos.core.sim.MatchQueries;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Seeded random commands for one faction's player, drawn through {@code Rng}, never {@code java.util.Random}: 0 to 2
 * per call, mostly a Move or a Stop for a few of the faction's own units; about one in ten invalid, either another
 * faction's units (NOT_OWNER) or a target outside the match (OUT_OF_BOUNDS).
 */
public final class RandomCommands {
    /** The test kit's {@code Rng} stream, far from the core's own ({@code Streams}). */
    static final int STREAM = 1001;

    /** The most units one command names. */
    static final int MAX_UNITS = 8;

    private RandomCommands() {}

    public static List<Command> next(long seed, long tick, MatchQueries q, FactionSlot faction) {
        int count = Rng.below(seed, STREAM, tick, faction.value(), 0, 3);
        if (count == 0) {
            return List.of();
        }
        int[] own = q.unitHandlesOf(faction);
        List<Command> commands = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            Command command = one(seed, tick, faction.value() * 4L + k, q, faction, own);
            if (command != null) {
                commands.add(command);
            }
        }
        return commands;
    }

    private static @Nullable Command one(
            long seed, long tick, long key, MatchQueries q, FactionSlot faction, int[] own) {
        MatchBounds bounds = q.config().bounds();
        int roll = Rng.below(seed, STREAM, tick, key, 1, 20);
        if (roll == 0) {
            int[] theirs = othersOf(q, faction);
            if (theirs.length > 0) {
                return Move.replace(pick(seed, tick, key, theirs), inside(seed, tick, key, bounds));
            }
        }
        if (own.length == 0) {
            return null;
        }
        UnitSet units = pick(seed, tick, key, own);
        if (roll <= 1) {
            double x = bounds.sizeX() + 0.5 + 8 * Rng.unit(seed, STREAM, tick, key, 2);
            return Move.replace(units, new Vec2d(x, 0.5 + (bounds.sizeZ() - 1) * Rng.unit(seed, STREAM, tick, key, 3)));
        }
        if (roll < 6) {
            return new Stop(units);
        }
        return Move.replace(units, inside(seed, tick, key, bounds));
    }

    /** 1 to {@link #MAX_UNITS} consecutive handles of {@code handles}, from a random start, wrapping around. */
    private static UnitSet pick(long seed, long tick, long key, int[] handles) {
        int first = Rng.below(seed, STREAM, tick, key, 4, handles.length);
        int n = 1 + Rng.below(seed, STREAM, tick, key, 5, Math.min(handles.length, MAX_UNITS));
        int[] picked = new int[n];
        for (int j = 0; j < n; j++) {
            picked[j] = handles[(first + j) % handles.length];
        }
        return UnitSet.of(picked);
    }

    /** A target at least half a block inside the match. */
    private static Vec2d inside(long seed, long tick, long key, MatchBounds bounds) {
        return new Vec2d(
                0.5 + (bounds.sizeX() - 1) * Rng.unit(seed, STREAM, tick, key, 6),
                0.5 + (bounds.sizeZ() - 1) * Rng.unit(seed, STREAM, tick, key, 7));
    }

    private static int[] othersOf(MatchQueries q, FactionSlot faction) {
        int[] all = q.unitHandles();
        int[] own = q.unitHandlesOf(faction);
        int[] others = new int[all.length - own.length];
        int n = 0;
        int j = 0;
        for (int handle : all) { // both sorted
            if (j < own.length && own[j] == handle) {
                j++;
            } else {
                others[n++] = handle;
            }
        }
        return others;
    }
}
