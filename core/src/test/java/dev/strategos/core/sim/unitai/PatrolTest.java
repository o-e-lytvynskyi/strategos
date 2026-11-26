package dev.strategos.core.sim.unitai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Patrol;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.TemplateId;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchSnapshot;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.sim.UnitCreated;
import dev.strategos.core.sim.order.OrderKind;
import dev.strategos.core.sim.persist.MatchSnapshotCodec;
import dev.strategos.core.sim.persist.Migrations;
import dev.strategos.core.testkit.SimHarness;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ORD-07.1 in L1: a patrol loops its points for ever (A to B and back, several cycles), fights an enemy it meets on a
 * leg and then walks the leg on, is saved mid-patrol with its points and restores equal, and replays to the live hash.
 * A patrol is refused for no point, too many, or one out of bounds; a queued order waits behind it.
 */
class PatrolTest {
    private static final FactionSlot F0 = new FactionSlot(0);
    private static final FactionSlot F1 = new FactionSlot(1);
    private static final PlayerSlot P0 = new PlayerSlot(0);
    private static final Vec2d A = new Vec2d(10, 16);
    private static final Vec2d B = new Vec2d(50, 16);

    private static int[] spawn(SimHarness h, TemplateId template, FactionSlot faction, Vec2d at, int count) {
        h.submit(PlayerSlot.ADMIN, new SpawnUnits(template, faction, at, count, 1));
        return h.step().events().stream()
                .filter(UnitCreated.class::isInstance)
                .mapToInt(e -> ((UnitCreated) e).handle())
                .toArray();
    }

    private static MatchState state(SimHarness h) {
        return KernelAccess.state(h.core());
    }

    private static double x(SimHarness h, int unit) {
        return state(h).motion().x(Handle.index(unit));
    }

    private static int kind(SimHarness h, int unit) {
        return state(h).orders().kind(Handle.index(unit));
    }

    private static CommandResult lastResult(SimHarness h) {
        List<CommandResult> all = h.results();
        return all.get(all.size() - 1);
    }

    /** The ends the unit reaches, in order, over {@code ticks}: 'A' within 2 blocks of A, 'B' within 2 of B. */
    private static String ends(SimHarness h, int unit, int ticks) {
        StringBuilder out = new StringBuilder();
        for (int t = 0; t < ticks; t++) {
            h.step();
            double x = x(h, unit);
            char at = Math.abs(x - A.x()) < 2 ? 'A' : Math.abs(x - B.x()) < 2 ? 'B' : 0;
            if (at != 0 && (out.isEmpty() || out.charAt(out.length() - 1) != at)) {
                out.append(at);
            }
        }
        return out.toString();
    }

    @Test
    void aPatrolLoopsBetweenItsPointsForSeveralCycles() {
        SimHarness h = SimHarness.builder().size(72, 32).build();
        int unit = spawn(h, TemplateSet.INFANTRY, F0, A, 1)[0];
        h.submit(P0, Patrol.replace(UnitSet.of(unit), List.of(A, B)));

        String visited = ends(h, unit, 1_500);

        assertThat(visited).as("A, B, A, B ... the loop closes").startsWith("ABABAB");
        assertThat(kind(h, unit)).as("it never ends by itself").isEqualTo(OrderKind.PATROL);
        assertThat(state(h).orders().anchorCount(Handle.index(unit))).isEqualTo(2);
        assertThat(h.results()).anySatisfy(r -> assertThat(r.outcome()).isEqualTo(CommandOutcome.ACCEPTED));
    }

    @Test
    void aSinglePointPatrolsBetweenTheUnitsOwnPlaceAndThePoint() {
        SimHarness h = SimHarness.builder().size(72, 32).build();
        int unit = spawn(h, TemplateSet.INFANTRY, F0, A, 1)[0];
        double startX = x(h, unit);
        h.submit(P0, Patrol.replace(UnitSet.of(unit), List.of(B)));
        h.step();

        assertThat(state(h).orders().anchorCount(Handle.index(unit))).isEqualTo(2);
        assertThat(state(h).orders().anchor(Handle.index(unit), 0).x()).isEqualTo(startX);
        assertThat(state(h).orders().targetX(Handle.index(unit)))
                .as("walks to the point first")
                .isEqualTo(B.x());
        assertThat(ends(h, unit, 1_500)).as("from A to the point and back").startsWith("ABAB");
    }

    /** An enemy met on the leg is fought (an engagement goes before the patrol) and the leg is walked on after it. */
    @Test
    void aPatrolFightsWhatItMeetsThenResumesTheLeg() {
        SimHarness h = SimHarness.builder().size(72, 32).build();
        int unit = spawn(h, TemplateSet.INFANTRY, F0, A, 1)[0];
        int[] met = spawn(h, TemplateSet.DEBUG_WALKER, F1, new Vec2d(30, 16), 1);
        h.submit(P0, Patrol.replace(UnitSet.of(unit), List.of(A, B)));
        boolean fought = false;
        int resumedAt = -1;
        for (int t = 0; t < 2_000 && resumedAt < 0; t++) {
            h.step();
            fought |= kind(h, unit) == OrderKind.ENGAGE;
            if (fought && kind(h, unit) == OrderKind.PATROL) {
                resumedAt = t;
            }
        }

        assertThat(fought).as("it engaged the enemy on the leg").isTrue();
        assertThat(h.queries().unit(met[0])).as("the enemy died").isNull();
        assertThat(resumedAt)
                .as("the patrol came back to the front of the queue")
                .isNotNegative();
        assertThat(state(h).orders().size(Handle.index(unit))).isEqualTo(1);
        assertThat(ends(h, unit, 600)).as("it walked the leg on to B").startsWith("B");
    }

    @Test
    void aNewOrderReplacesAPatrolAndAQueuedOneWaitsBehindIt() {
        SimHarness h = SimHarness.builder().size(72, 32).build();
        int unit = spawn(h, TemplateSet.INFANTRY, F0, A, 1)[0];
        h.submit(P0, Patrol.replace(UnitSet.of(unit), List.of(A, B)));
        h.submit(P0, new Move(UnitSet.of(unit), new Vec2d(30, 28), QueueMode.QUEUE, 0));
        h.runTicks(900);

        int i = Handle.index(unit);
        assertThat(kind(h, unit)).as("a queued move never runs").isEqualTo(OrderKind.PATROL);
        assertThat(state(h).orders().size(i)).isEqualTo(2);
        assertThat(state(h).motion().z(i)).isCloseTo(16, org.assertj.core.data.Offset.offset(1.5));

        h.submit(P0, Move.replace(UnitSet.of(unit), new Vec2d(30, 28)));
        h.step();
        assertThat(kind(h, unit)).isEqualTo(OrderKind.MOVE);
        assertThat(state(h).orders().anchorCount(i))
                .as("the points go with the order")
                .isZero();
    }

    @Test
    void aPatrolIsRefusedForNoPointTooManyOrOneOutOfBounds() {
        SimHarness h = SimHarness.builder().size(72, 32).build();
        int unit = spawn(h, TemplateSet.INFANTRY, F0, A, 1)[0];
        List<Vec2d> many = new ArrayList<>();
        for (int k = 0; k <= Patrol.MAX_POINTS; k++) {
            many.add(new Vec2d(10 + k, 16));
        }
        h.submit(P0, Patrol.replace(UnitSet.of(unit), List.of()));
        h.step();
        assertThat(lastResult(h).firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);
        h.submit(P0, Patrol.replace(UnitSet.of(unit), many));
        h.step();
        assertThat(lastResult(h).firstReason()).isEqualTo(RejectReason.INVALID_ARGUMENT);
        h.submit(P0, Patrol.replace(UnitSet.of(unit), List.of(A, new Vec2d(900, 16))));
        h.step();
        assertThat(lastResult(h).firstReason()).isEqualTo(RejectReason.OUT_OF_BOUNDS);
        h.submit(new PlayerSlot(1), Patrol.replace(UnitSet.of(unit), List.of(A, B)));
        h.step();
        assertThat(lastResult(h).firstReason())
                .as("another's unit")
                .isIn(RejectReason.NOT_OWNER, RejectReason.UNKNOWN_TARGET);
        assertThat(kind(h, unit)).isEqualTo(OrderKind.NONE);
    }

    private static JsonObject encode(MatchSnapshot snapshot) {
        return MatchSnapshotCodec.encode(JsonOps.INSTANCE, snapshot).getAsJsonObject();
    }

    private static MatchSnapshot decode(JsonObject json) {
        return MatchSnapshotCodec.decode(new Dynamic<>(JsonOps.INSTANCE, json), Migrations.production())
                .getOrThrow();
    }

    /** Mid-patrol, saved through the codec: the points and the leg come back, and the restored match hashes as the live. */
    @Test
    void aRestoreThroughTheSaveCodecMidPatrolIsEqualAndKeepsLooping() {
        SimHarness live = SimHarness.builder().size(72, 32).build();
        int[] squad = spawn(live, TemplateSet.INFANTRY, F0, A, 3);
        live.submit(P0, Patrol.replace(UnitSet.of(squad), List.of(A, new Vec2d(40, 24), B)));
        int i = Handle.index(squad[0]);
        assertThat(live.runUntil(() -> state(live).orders().slot(i) == 2, 1_000))
                .as("on the last leg")
                .isTrue();
        live.runTicks(30);
        assertThat(state(live).orders().kind(i)).isEqualTo(OrderKind.PATROL);

        MatchSnapshot saved = decode(encode(live.core().snapshot()));
        assertThat(saved).isEqualTo(live.core().snapshot());
        SimHarness restored = SimHarness.restore(saved, state(live).templates());

        assertThat(restored.stateHash()).isEqualTo(live.stateHash());
        assertThat(KernelAccess.state(restored.core()).orders().saved(i))
                .isEqualTo(state(live).orders().saved(i));
        assertThat(KernelAccess.state(restored.core()).orders().anchorCount(i)).isEqualTo(3);
        for (int t = 0; t < 8; t++) {
            live.runTicks(40);
            restored.runTicks(40);
            assertThat(restored.stateHash())
                    .as("after %d more ticks", 40 * (t + 1))
                    .isEqualTo(live.stateHash());
        }
    }

    @Test
    void thePointsAreSavedOnlyWhileAPatrolHoldsThem() {
        SimHarness h = SimHarness.builder().size(72, 32).build();
        int unit = spawn(h, TemplateSet.INFANTRY, F0, A, 1)[0];
        h.submit(P0, Move.replace(UnitSet.of(unit), B));
        h.step();
        String plain = encode(h.core().snapshot()).toString();
        assertThat(plain).doesNotContain("anchors");
        long moving = h.stateHash();

        h.submit(P0, Patrol.replace(UnitSet.of(unit), List.of(A, B)));
        h.step();
        assertThat(encode(h.core().snapshot()).toString()).contains("anchors");
        assertThat(h.stateHash()).isNotEqualTo(moving);
    }

    @Test
    void aReplayOfThePatrolsJournalReachesTheLiveHash() {
        SimHarness h = SimHarness.builder().size(72, 32).build();
        int[] squad = spawn(h, TemplateSet.INFANTRY, F0, A, 2);
        spawn(h, TemplateSet.DEBUG_WALKER, F1, new Vec2d(30, 16), 2);
        h.submit(P0, Patrol.replace(UnitSet.of(squad), List.of(A, B)));
        h.runTicks(700);

        assertThat(h.replay().matches()).isTrue();
    }
}
