package dev.strategos.core.sim.move;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.gson.JsonElement;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.CorePaths;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.MotionStore;
import dev.strategos.core.sim.Notice;
import dev.strategos.core.sim.OrderFinished;
import dev.strategos.core.sim.Stuck;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.nav.MobilityClass;
import dev.strategos.core.sim.nav.PageBuilder;
import dev.strategos.core.sim.nav.PageCoord;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.sim.stats.Modifier;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.core.testkit.VoxelFixtures;
import dev.strategos.core.testkit.VoxelWorld;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PATH-07.1 and .2 (spec §5.11): a unit that cannot get anywhere climbs the stuck ladder's rungs in order, at 40, 100,
 * 200, 400 and 600 ticks without progress, its count going on through a new route and the nudge, and its order fails
 * at the last with a STUCK notice; no rung takes a unit out of the match space; a unit walled in by others walks out
 * with avoidance off at the third rung; a detour and a ghost walk survive a save with the state hash.
 */
class StuckLadderTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final int ROCK = 7; // the source of the modifiers that hold units still

    @Test
    void rungsFollowTheCount() {
        int[] counts = {0, 39, 40, 99, 100, 199, 200, 399, 400, 599, 600, 10_000};
        int[] rungs = new int[counts.length];
        for (int k = 0; k < counts.length; k++) {
            rungs[k] = StuckLadder.rung(counts[k]);
        }

        assertThat(rungs).containsExactly(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5);
    }

    @Test
    void aUnitThatCannotMoveClimbsEveryRungAndFailsAt600() {
        VoxelWorld w = VoxelFixtures.flat(48, 32, 16, 1);
        SimHarness h = harness(w);
        int unit = h.spawn(F0, new Vec2d(8.5, 16.5), 1, 0)[0];
        int i = Handle.index(unit);
        hold(h, unit);
        List<int[]> climbed = new ArrayList<>(); // rung, count, detour ticks, ghost ticks
        List<Vec2d> at = new ArrayList<>();
        List<OrderFinished> finished = new ArrayList<>();
        state(h).bus().subscribe(Stuck.class, m -> {
            MotionStore motion = state(h).motion();
            climbed.add(new int[] {m.rung(), motion.stuck(i), motion.detourTicks(i), motion.ghostTicks(i)});
            at.add(new Vec2d(motion.x(i), motion.z(i)));
        });
        state(h).bus().subscribe(OrderFinished.class, finished::add);
        order(h, unit, 40.5, 16.5);
        List<Integer> counts = new ArrayList<>();
        counts.add(state(h).motion().stuck(i));

        for (int t = 0; t < 700 && unit(h, unit).state() == UnitState.WALKING; t++) {
            if (t == 25) { // a wall ahead: the route bends round it, which is no progress
                w.fill(20, 1, 10, 20, 3, 22, VoxelFixtures.SOLID);
                for (PageCoord page : List.of(new PageCoord(1, 0), new PageCoord(1, 1))) {
                    h.publish(TerrainPage.build(page, w.slice(page), new PageBuilder(MobilityClass.INFANTRY)));
                }
            }
            h.step();
            counts.add(state(h).motion().stuck(i));
        }

        assertThat(climbed).extracting(c -> c[0]).containsExactly(1, 2, 3, 4, 5);
        assertThat(climbed).extracting(c -> c[1]).containsExactly(40, 100, 200, 400, 600);
        assertThat(climbed.get(1)[2]).as("a detour on the second rung").isEqualTo(StuckLadder.STEP_TICKS);
        assertThat(climbed.get(2)[3]).as("a ghost walk on the third").isEqualTo(StuckLadder.GHOST_TICKS);
        assertThat(Math.hypot(
                        at.get(3).x() - at.get(2).x(), at.get(3).z() - at.get(2).z()))
                .as("the nudge along the path, to the first free spot a block on")
                .isCloseTo(1.0, within(1e-9));
        for (int k = 1; k < counts.size(); k++) {
            assertThat(counts.get(k)).as("the count on tick %s", k).isEqualTo(counts.get(k - 1) + 1);
        }
        assertThat(counts.get(counts.size() - 1)).isEqualTo(StuckLadder.FAIL);
        assertThat(finished).containsExactly(new OrderFinished(unit, true));
        assertThat(unit(h, unit).state()).isEqualTo(UnitState.IDLE);
        assertThat(h.events(Notice.class))
                .filteredOn(n -> n.kind() == NoticeKind.STUCK)
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.handle()).isEqualTo(unit);
                    assertThat(n.faction()).isEqualTo(F0);
                });
        assertThat(h.queries().ladderRungs()).containsExactly(0, 1, 1, 1, 1, 1);
        MotionStore.Extra motion = h.queries().motion(unit);
        assertThat(motion).isNotNull();
        assertThat(motion.rung()).isEqualTo(Stuck.GIVE_UP);
    }

    @Test
    void noRungTakesAUnitOutOfTheMatchSpace() {
        SimHarness h = harness(VoxelFixtures.flat(48, 32, 16, 1));
        int unit = h.spawn(F0, new Vec2d(0.5, 0.5), 1, 0)[0];
        int i = Handle.index(unit);
        hold(h, unit);
        for (Vec2d rock : List.of(new Vec2d(1.5, 0.5), new Vec2d(0.5, 1.5), new Vec2d(1.5, 1.5))) {
            hold(h, h.spawn(F0, rock, 1, 0)[0]);
        }
        List<double[]> detours = new ArrayList<>();
        state(h).bus().subscribe(Stuck.class, m -> {
            if (m.rung() == 2) {
                detours.add(new double[] {
                    state(h).motion().detourX(i), state(h).motion().detourZ(i)
                });
            }
        });
        order(h, unit, 24.5, 0.5);
        double xBefore = 0;

        for (int t = 0; t < 700 && unit(h, unit).state() == UnitState.WALKING; t++) {
            if (state(h).motion().stuck(i) == StuckLadder.NUDGE - 1) {
                xBefore = unit(h, unit).x();
            }
            h.step();
            UnitView u = unit(h, unit);
            assertThat(u.x()).as("x on tick %s", t).isBetween(0.0, 48.0);
            assertThat(u.z()).as("z on tick %s", t).isBetween(0.0, 32.0);
        }

        assertThat(detours)
                .as("the nearest free spot inside the space, past the units round the corner")
                .singleElement()
                .satisfies(d -> assertThat(d).containsExactly(2.5, 0.5));
        assertThat(unit(h, unit).x())
                .as("nudged past the unit in its way, to the first free column")
                .isGreaterThan(xBefore)
                .isCloseTo(2.0, within(1e-3)); // on the unit behind it, avoidance edges it on
        assertThat(h.queries().ladderRungs()).containsExactly(0, 1, 1, 1, 1, 1);
    }

    @Test
    void aUnitWalledInByOthersWalksOutWithAvoidanceOff() {
        SimHarness h = harness(VoxelFixtures.flat(48, 32, 16, 1));
        int unit = h.spawn(F0, new Vec2d(8.5, 16.5), 1, 0)[0];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dz != 0) {
                    hold(h, h.spawn(F0, new Vec2d(8.5 + dx, 16.5 + dz), 1, 0)[0]);
                }
            }
        }
        List<Integer> rungs = new ArrayList<>();
        List<OrderFinished> finished = new ArrayList<>();
        state(h).bus().subscribe(Stuck.class, m -> rungs.add(m.rung()));
        state(h).bus().subscribe(OrderFinished.class, finished::add);
        order(h, unit, 30.5, 16.5);

        boolean idle = h.runUntil(() -> unit(h, unit).state() == UnitState.IDLE, 800);

        assertThat(idle).isTrue();
        assertThat(rungs).as("free at the ghost walk").containsExactly(1, 2, 3);
        assertThat(finished).containsExactly(new OrderFinished(unit, false));
        assertThat(unit(h, unit).x()).isEqualTo(30.5);
    }

    @Test
    void aDetourAndAGhostWalkSurviveASaveWithTheHash() {
        SimHarness h = harness(VoxelFixtures.flat(48, 32, 16, 1));
        int unit = h.spawn(F0, new Vec2d(8.5, 16.5), 1, 0)[0];
        int i = Handle.index(unit);
        hold(h, unit);
        order(h, unit, 40.5, 16.5);
        h.runUntil(() -> state(h).motion().stuck(i) == StuckLadder.STEP + 5, 200);
        MatchSnapshot detour = h.core().snapshot();
        h.runUntil(() -> state(h).motion().stuck(i) == StuckLadder.GHOST + 5, 200);
        MatchSnapshot ghost = h.core().snapshot();

        for (MatchSnapshot snapshot : List.of(detour, ghost)) {
            JsonElement json = MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot);
            MatchSnapshot decoded = MatchSnapshotCodec.decode(
                            new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                    .getOrThrow();
            MatchCore original = MatchCore.restore(snapshot, TemplateSet.builtin(), CorePorts.of(() -> 0L));
            MatchCore restored = MatchCore.restore(decoded, TemplateSet.builtin(), CorePorts.of(() -> 0L));
            MatchCore cleared = MatchCore.restore(snapshot, TemplateSet.builtin(), CorePorts.of(() -> 0L));
            MotionStore motion = KernelAccess.state(cleared).motion();
            motion.setDetour(i, 0, 0, 0);
            motion.setGhostTicks(i, 0);

            assertThat(decoded).isEqualTo(snapshot);
            assertThat(restored.stateHash())
                    .isEqualTo(original.stateHash())
                    .as("the ladder's walk is hashed")
                    .isNotEqualTo(cleared.stateHash());
        }
        assertThat(detour.units().get(0).motion().ladder().detourTicks()).isPositive();
        assertThat(ghost.units().get(0).motion().ladder().ghostTicks()).isPositive();
    }

    /** Holds unit {@code handle} still: its walk speed 0, as boxed in as a unit can be. */
    private static void hold(SimHarness h, int handle) {
        state(h).stats().addPersonal(handle, Modifier.replace(CorePaths.WALK_SPEED, 0, ROCK));
    }

    private static SimHarness harness(VoxelWorld world) {
        SimHarness h = SimHarness.builder().size(world.sizeX(), world.sizeZ()).build();
        h.publishAll(world);
        return h;
    }

    private static void order(SimHarness h, int unit, double x, double z) {
        h.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(unit), new Vec2d(x, z)));
        h.step();
    }

    private static UnitView unit(SimHarness h, int unit) {
        UnitView view = h.queries().unit(unit);
        assertThat(view).isNotNull();
        return view;
    }

    private static MatchState state(SimHarness h) {
        return KernelAccess.state(h.core());
    }
}
