package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.shared.DetMath;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.Attacked;
import dev.strategos.core.sim.CorePorts;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * FLEEING (CMB-05, M5 §2.4): the flight of a worker (flee distance 8) from a blow is a state that plans its point
 * again from the attacker's place each think, ends 8 blocks from the attacker or when the attacker is gone, turns on a
 * blow from another, and is saved and resumed exactly.
 */
class FleeingTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final double X = 40.5;
    private static final double Z = 40.5;
    private static final double FLEE = 8;

    private static MatchState state(SimHarness h) {
        return KernelAccess.state(h.core());
    }

    private static int spawn(SimHarness h, TemplateId template, FactionSlot faction, double x, double z) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, faction, new Vec2d(x, z), 1, 0));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .findFirst()
                .orElseThrow();
    }

    private static double apart(SimHarness h, int a, int b) {
        MatchState s = state(h);
        int i = Handle.index(a);
        int j = Handle.index(b);
        return DetMath.length(
                s.motion().x(i) - s.motion().x(j), s.motion().z(i) - s.motion().z(j));
    }

    private static void put(SimHarness h, int unit, double x, double z) {
        int i = Handle.index(unit);
        state(h).motion().set(i, x, state(h).motion().y(i), z, 0);
    }

    private static boolean fleeing(SimHarness h, int unit) {
        return UnitAi.leaf(state(h).ai().code(Handle.index(unit))).equals(UnitAi.FLEEING);
    }

    private SimHarness blow(int[] ids) {
        SimHarness h = SimHarness.builder().size(96, 96).build();
        ids[0] = spawn(h, TemplateSet.WORKER, F0, X, Z);
        ids[1] = spawn(h, TemplateSet.DEBUG_WALKER, F1, X - 2, Z);
        state(h).bus().publish(new Attacked(ids[0], ids[1]));
        return h;
    }

    @Test
    void aBlowStartsAFlightFromTheAttackerHoldingItsHandle() {
        int[] ids = new int[2];
        SimHarness h = blow(ids);
        MatchState s = state(h);
        int w = Handle.index(ids[0]);
        assertThat(fleeing(h, ids[0])).isTrue();
        assertThat(s.orders().kind(w)).isEqualTo(OrderKind.FLEE);
        assertThat(s.orders().target(w)).as("the attacker is remembered").isEqualTo(ids[1]);
        assertThat(DetMath.length(
                        s.orders().targetX(w) - s.motion().x(Handle.index(ids[1])),
                        s.orders().targetZ(w) - s.motion().z(Handle.index(ids[1]))))
                .as("its point is the flee distance from the attacker")
                .isBetween(FLEE - 0.01, FLEE + 0.01);
    }

    @Test
    void thePointIsPlannedAgainFromAMovingAttacker() {
        int[] ids = new int[2];
        SimHarness h = blow(ids);
        MatchState s = state(h);
        int w = Handle.index(ids[0]);
        h.runTicks(5);
        assertThat(s.orders().targetX(w)).as("first east, away from the west").isGreaterThan(X + 3);
        put(h, ids[1], s.motion().x(w), s.motion().z(w) + 2); // now it stands south of the worker
        h.runTicks(5); // the next think
        assertThat(s.orders().kind(w)).isEqualTo(OrderKind.FLEE);
        assertThat(s.orders().targetZ(w))
                .as("the point turns north, away from the attacker's new place")
                .isLessThan(s.motion().z(w) - 4);
        assertThat(DetMath.length(
                        s.orders().targetX(w) - s.motion().x(Handle.index(ids[1])),
                        s.orders().targetZ(w) - s.motion().z(Handle.index(ids[1]))))
                .isBetween(FLEE - 0.01, FLEE + 0.01);
    }

    @Test
    void theFlightEndsOnceTheWorkerIsEightBlocksOff() {
        int[] ids = new int[2];
        SimHarness h = blow(ids);
        assertThat(h.runUntil(() -> state(h).orders().size(Handle.index(ids[0])) == 0, 200))
                .isTrue();
        assertThat(apart(h, ids[0], ids[1])).isBetween(FLEE - 0.01, FLEE + 2);
        assertThat(fleeing(h, ids[0])).isFalse();
    }

    @Test
    void theFlightEndsWhenTheAttackerIsGone() {
        int[] ids = new int[2];
        SimHarness h = blow(ids);
        h.runTicks(10);
        assertThat(fleeing(h, ids[0])).isTrue();
        state(h).removeUnit(ids[1]);
        h.runTicks(5);
        assertThat(fleeing(h, ids[0])).isFalse();
        assertThat(state(h).orders().size(Handle.index(ids[0]))).isZero();
    }

    @Test
    void aBlowFromAnotherAttackerTurnsTheFlight() {
        int[] ids = new int[2];
        SimHarness h = blow(ids);
        MatchState s = state(h);
        int w = Handle.index(ids[0]);
        h.runTicks(5);
        int second = spawn(
                h, TemplateSet.DEBUG_WALKER, F1, s.motion().x(w) + 2, s.motion().z(w));
        s.bus().publish(new Attacked(ids[0], second));
        assertThat(s.orders().size(w)).as("one flight, not two").isEqualTo(1);
        assertThat(s.orders().target(w)).isEqualTo(second);
        assertThat(s.orders().targetX(w))
                .as("now west, away from the second")
                .isLessThan(s.motion().x(w) - 4);
    }

    @Test
    void aSaveInMidFlightResumesWithEqualHashes() {
        int[] ids = new int[2];
        SimHarness h = blow(ids);
        h.runTicks(12);
        assertThat(fleeing(h, ids[0])).isTrue();
        MatchSnapshot saved = MatchSnapshotCodec.decode(
                        new Dynamic<>(
                                JsonOps.INSTANCE,
                                MatchSnapshotCodec.encode(
                                        JsonOps.INSTANCE, h.core().snapshot())),
                        Migrations.production())
                .getOrThrow();
        MatchCore resumed = MatchCore.restore(saved, TemplateSet.builtin(), CorePorts.of(() -> 0L));
        assertThat(resumed.stateHash()).isEqualTo(h.stateHash());
        for (int i = 0; i < 80; i++) {
            long tick = h.tick() + 1;
            h.core().step(new TickInputs(tick, List.of()));
            resumed.step(new TickInputs(tick, List.of()));
            assertThat(resumed.stateHash()).as("tick %d", tick).isEqualTo(h.stateHash());
        }
        assertThat(fleeing(h, ids[0])).as("the flight ended on both sides").isFalse();
    }
}
