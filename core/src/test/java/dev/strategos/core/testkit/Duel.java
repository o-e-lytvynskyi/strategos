package dev.strategos.core.testkit;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.testkit.DuelLayout.Kind;
import dev.strategos.core.testkit.DuelLayout.Spot;
import java.util.function.IntPredicate;

/**
 * The roster's duel (UNIT-07, M5 spec 2.7): two groups of equal cost meet on open flat ground ({@link DuelLayout}),
 * both told to attack the other, and the fight runs until one side is dead or the tick cap. A seed fixes everything:
 * which side stands west, each body's jitter and the unit each side opens on, so a matchup over many seeds measures
 * the templates and never one placement. Headless and quick: no terrain, no client.
 */
public final class Duel {
    static final int TICK_CAP = 3000;

    private static final TemplateSet SET = TemplateSet.builtin();
    private static final int MAP_X = 96;
    private static final int MAP_Z = 64;
    private static final double CENTER_X = 48;
    private static final double CENTER_Z = 32;
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);

    /** One duel: who won (0 or 1 for the first or second group, -1 for none), when, and who was left. */
    public record Result(int winner, int ticks, int aliveFirst, int aliveSecond) {}

    private Duel() {}

    /** The duel of {@code first} (faction 0) against {@code second} (faction 1) on flat ground. */
    public static Result run(Kind first, Kind second, long seed) {
        return run(first, second, seed, 0);
    }

    /** As {@link #run(Kind, Kind, long)}, the first group standing {@code hill} blocks above the second. */
    public static Result run(Kind first, Kind second, long seed, double hill) {
        int[] counts = DuelLayout.counts(first, second);
        var h = SimHarness.builder()
                .size(MAP_X, MAP_Z)
                .seed(seed)
                .templates(SET)
                .build();
        int west = DuelLayout.firstWest(seed) ? -1 : 1;
        h.publishAll(ground(hill, west));
        h.step(); // the terrain applies: without it bodies stand on a bare floor, and crowds fight differently
        MatchState s = KernelAccess.state(h.core());
        double gap = hill > 0 ? DuelLayout.HILL_GAP : DuelLayout.gap(first, second);
        int[] a = spawn(h, s, first, F0, counts[0], seed, 0, west, gap);
        int[] b = spawn(h, s, second, F1, counts[1], seed, 1, -west, gap);
        h.submit(PlayerSlot.ADMIN, Attack.replace(UnitSet.of(a), b[DuelLayout.target(seed, 0, b.length)]));
        h.submit(PlayerSlot.ADMIN, Attack.replace(UnitSet.of(b), a[DuelLayout.target(seed, 1, a.length)]));
        IntPredicate alive = handle -> s.entities().isAlive(handle);
        h.runUntil(() -> count(a, alive) == 0 || count(b, alive) == 0, TICK_CAP);
        int aliveA = count(a, alive);
        int aliveB = count(b, alive);
        int winner = aliveA > 0 && aliveB == 0 ? 0 : aliveB > 0 && aliveA == 0 ? 1 : -1;
        return new Result(winner, (int) h.tick(), aliveA, aliveB);
    }

    /** Wins of the first group over the duels of the committed seed set {@code seedSet}. */
    public static int wins(Kind first, Kind second, String seedSet, double hill) {
        return (int) Seeds.set(seedSet)
                .filter(seed -> run(first, second, seed, hill).winner() == 0)
                .count();
    }

    /**
     * A floor one block thick; with a hill, the first group's half of the map, from the centre line out, is
     * {@code hill} blocks higher (a plateau no one has to climb: the others stay below its edge).
     */
    private static VoxelWorld ground(double hill, int west) {
        VoxelWorld world = VoxelFixtures.flat(MAP_X, MAP_Z, 16, 1);
        if (hill > 0) {
            int from = west < 0 ? 0 : (int) CENTER_X;
            int to = west < 0 ? (int) CENTER_X - 1 : MAP_X - 1;
            world.fill(from, 1, 0, to, (int) hill, MAP_Z - 1, VoxelFixtures.SOLID);
        }
        return world;
    }

    private static int count(int[] handles, IntPredicate alive) {
        int n = 0;
        for (int handle : handles) {
            if (alive.test(handle)) n++;
        }
        return n;
    }

    private static int[] spawn(
            SimHarness h,
            MatchState s,
            Kind kind,
            FactionSlot faction,
            int count,
            long seed,
            int group,
            int side,
            double gap) {
        int[] handles = new int[count];
        for (int k = 0; k < count; k++) {
            Spot spot = DuelLayout.place(seed, group, k, count, side, gap, CENTER_X, CENTER_Z);
            h.submit(PlayerSlot.ADMIN, new SpawnUnits(kind.id(), faction, new Vec2d(spot.x(), spot.z()), 1, 0));
            handles[k] = h.step().events().stream()
                    .filter(UnitCreated.class::isInstance)
                    .map(UnitCreated.class::cast)
                    .mapToInt(UnitCreated::handle)
                    .findFirst()
                    .orElseThrow();
            // Spawned exactly, then turned to face the enemy; its height is the ground's
            int i = Handle.index(handles[k]);
            s.motion().set(i, spot.x(), s.motion().y(i), spot.z(), side < 0 ? 90 : -90);
        }
        return handles;
    }
}
