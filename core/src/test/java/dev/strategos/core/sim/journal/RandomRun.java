package dev.strategos.core.sim.journal;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Rng;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;

/**
 * A sparse seeded run with moves, stops and some refused commands, for the journal's tests: a command every few ticks
 * keeps one removed command visible at the next checkpoint. The seeded determinism runs use the denser
 * {@code SeededRuns}.
 */
final class RandomRun {
    private RandomRun() {}

    /** Two factions of 20 units on 32 x 32; every few ticks a player orders a random subset somewhere. */
    static SimHarness run(long seed, int ticks) {
        SimHarness h = SimHarness.builder().seed(seed).build();
        int[][] units = {
            h.spawn(new FactionSlot(0), new Vec2d(8, 8), 20, 6.0),
            h.spawn(new FactionSlot(1), new Vec2d(24, 24), 20, 6.0)
        };
        for (int t = 0; t < ticks; t++) {
            long tick = h.tick();
            for (int faction = 0; faction < 2; faction++) {
                if (Rng.below(seed, 9, tick, faction, 0, 4) != 0) {
                    continue;
                }
                int[] own = units[faction];
                int first = Rng.below(seed, 9, tick, faction, 1, own.length);
                int count = 1 + Rng.below(seed, 9, tick, faction, 2, 5);
                int[] picked = new int[Math.min(count, own.length - first)];
                System.arraycopy(own, first, picked, 0, picked.length);
                h.submit(new PlayerSlot(faction), command(seed, tick, faction, picked, units[1 - faction]));
            }
            h.step();
        }
        return h;
    }

    private static Command command(long seed, long tick, int faction, int[] own, int[] theirs) {
        int kind = Rng.below(seed, 9, tick, faction, 3, 10);
        if (kind == 0) {
            return new Stop(UnitSet.of(own));
        }
        if (kind == 1) { // about 10 %: someone else's units, refused NOT_OWNER
            return Move.replace(UnitSet.of(theirs[0]), new Vec2d(16, 16));
        }
        double x = 0.5 + 31 * Rng.unit(seed, 9, tick, faction, 4);
        double z = 0.5 + 31 * Rng.unit(seed, 9, tick, faction, 5);
        return Move.replace(UnitSet.of(own), new Vec2d(x, z));
    }
}
