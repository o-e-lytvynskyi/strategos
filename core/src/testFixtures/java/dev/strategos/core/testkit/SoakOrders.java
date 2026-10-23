package dev.strategos.core.testkit;

import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchQueries;
import java.util.Arrays;
import java.util.SplittableRandom;
import org.jspecify.annotations.Nullable;

/**
 * PERF-05's orders (M1 spec §8.5): from a seed, every {@value #EVERY} ticks a group of 1 to {@value #MOST} live units of
 * one of two factions, drawn at random, sent to a random land column of the proving ground (no water on top; a column
 * no unit can reach sends them to the nearest that they can, PATH-04). The same seed against the same match gives the
 * same orders, so the crowd soak and its L1 replay draw them alike.
 */
public final class SoakOrders {
    public static final int EVERY = 20;
    public static final int MOST = 60;
    private static final int MARGIN = 8;

    private final SplittableRandom random;
    private final ProvingGround ground;
    private long groups;
    private long units;

    public SoakOrders(long seed, ProvingGround ground) {
        this.random = new SplittableRandom(seed);
        this.ground = ground;
    }

    /** The order due at {@code tick}, the tick the match steps next, or null between orders or without units. */
    public @Nullable Move next(long tick, MatchQueries q) {
        if (tick % EVERY != 0) {
            return null;
        }
        int[] live = q.unitHandlesOf(new FactionSlot(random.nextInt(2)));
        if (live.length == 0) {
            return null;
        }
        int n = 1 + random.nextInt(Math.min(MOST, live.length));
        int[] pool = live.clone();
        for (int k = 0; k < n; k++) { // a partial Fisher-Yates shuffle: the first n are the group
            int pick = k + random.nextInt(pool.length - k);
            int swap = pool[k];
            pool[k] = pool[pick];
            pool[pick] = swap;
        }
        groups++;
        units += n;
        return Move.replace(UnitSet.of(Arrays.copyOf(pool, n)), land());
    }

    /** Group orders so far. */
    public long groups() {
        return groups;
    }

    /** Unit orders so far: each unit of a group order counts one. */
    public long unitOrders() {
        return units;
    }

    private Vec2d land() {
        while (true) {
            int x = MARGIN + random.nextInt(ground.size() - 2 * MARGIN);
            int z = MARGIN + random.nextInt(ground.size() - 2 * MARGIN);
            if (ground.block(x, ground.top(x, z), z) != ProvingGround.WATER) {
                return new Vec2d(x + 0.5, z + 0.5);
            }
        }
    }
}
