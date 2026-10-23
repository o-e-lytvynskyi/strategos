package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.match;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.replicate.Visibility;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.Replication;
import dev.strategos.mod.server.net.payload.UiQueryPayload;
import dev.strategos.mod.server.net.payload.UnitDetailPayload;
import dev.strategos.mod.server.net.payload.UnitHudPayload;
import dev.strategos.mod.server.net.payload.UnitSummaryPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.phys.Vec3;

/** Replication tier 2 to mock players (NET-08): who gets which units' summaries, and how often. */
public final class ReplicationTests {
    private ReplicationTests() {}

    /**
     * NET-08.3: over 2 s, a seated player of faction 0 gets four frames, holding its three units and the two enemies it
     * sees, never the third, which the test's visibility hides; the player of faction 1, the positive control in the
     * same setup, gets its own three and all three of faction 0.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void summariesAtTwoHertzOwnAndVisibleOnly(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer a = seated(h, rt, "strategos-rep-a", 0);
        MockViewer b = seated(h, rt, "strategos-rep-b", 1);
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(8, 8), 3, 2));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(24, 24), 3, 2));
        Replication replication = StrategosServer.get().replication();
        var state = new Object() {
            int hidden = Handle.NONE;
            final List<UnitSummary> toA = new ArrayList<>();
            final List<UnitSummary> toB = new ArrayList<>();
        };
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().unitHandles().length == 6, "the units are not there"))
                .thenExecute(() -> {
                    state.hidden = core(rt).queries().unitHandlesOf(new FactionSlot(1))[0];
                    int hidden = state.hidden;
                    replication.visibility(rt.id(), (viewer, unit) -> unit.handle() != hidden);
                    a.poll();
                    b.poll();
                    a.capture().clear();
                    b.capture().clear();
                })
                .thenExecuteAfter(40, () -> {
                    collect(a, state.toA, rt);
                    collect(b, state.toB, rt);
                    replication.visibility(rt.id(), Visibility.ALL);
                    check(state.toA.size() == 4, "faction 0 got " + state.toA.size() + " frames in 2 s");
                    check(state.toB.size() == 4, "faction 1 got " + state.toB.size() + " frames in 2 s");
                    for (UnitSummary s : state.toA) {
                        check(
                                s.units().stream().noneMatch(u -> u.index() == Handle.index(state.hidden)),
                                "faction 0's frame holds the hidden unit");
                        check(
                                s.units().size() == 5,
                                "faction 0's frame holds " + s.units().size() + " units, not 5");
                    }
                    for (UnitSummary s : state.toB) {
                        check(
                                s.units().size() == 6,
                                "faction 1's frame holds " + s.units().size() + " units, not 6");
                    }
                    a.close();
                    b.close();
                })
                .thenSucceed();
    }

    /** NET-08.4: a {@code ui_query} for an own unit and an enemy is answered with the own unit's details only. */
    @StrategosTest(timeoutTicks = 200)
    public static void detailAnswersOwnUnitsOnly(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer a = seated(h, rt, "strategos-rep-d", 0);
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(8, 8), 1, 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(24, 24), 1, 0));
        var state = new Object() {
            int own;
            int enemy;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().unitHandles().length == 2, "the units are not there"))
                .thenExecute(() -> {
                    state.own = core(rt).queries().unitHandlesOf(new FactionSlot(0))[0];
                    state.enemy = core(rt).queries().unitHandlesOf(new FactionSlot(1))[0];
                    a.poll();
                    a.capture().clear();
                    a.send(new UiQueryPayload(new UiQuery(List.of(state.enemy, state.own))));
                })
                .thenExecuteAfter(4, () -> {
                    a.poll();
                    List<UnitDetail> details = new ArrayList<>();
                    for (ClientboundCustomPayloadPacket p : a.capture().of(ClientboundCustomPayloadPacket.class)) {
                        if (p.payload() instanceof UnitDetailPayload d) {
                            details.add(d.detail());
                        }
                    }
                    check(!details.isEmpty(), "no detail came");
                    for (UnitDetail d : details) {
                        check(
                                d.units().size() == 1 && d.units().get(0).handle() == state.own,
                                "the detail holds " + d.units());
                    }
                    a.close();
                })
                .thenSucceed();
    }

    /**
     * CMB-06.1 (NET-08's {@code unit_hud}): two hurt units, one of each faction; faction 0's player, which the test's
     * visibility keeps from seeing faction 1's, gets only its own unit's hit points; faction 1's player, the positive
     * control, gets its own unit's and, seeing everything, faction 0's.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void hudReachesOnlyThoseWhoSee(GameTestHelper h) {
        MatchRuntime rt = match(h);
        MockViewer a = seated(h, rt, "strategos-rep-h", 0);
        MockViewer b = seated(h, rt, "strategos-rep-i", 1);
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(8, 8), 1, 0));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(24, 24), 1, 0));
        Replication replication = StrategosServer.get().replication();
        var state = new Object() {
            int own;
            int enemy;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().unitHandles().length == 2, "the units are not there"))
                .thenExecute(() -> {
                    state.own = core(rt).queries().unitHandlesOf(new FactionSlot(0))[0];
                    state.enemy = core(rt).queries().unitHandlesOf(new FactionSlot(1))[0];
                    int hidden = state.enemy;
                    replication.visibility(rt.id(), (viewer, unit) -> viewer.value() != 0 || unit.handle() != hidden);
                    rt.damage(new ExternalDamage(
                            state.own, ExternalDamage.Kind.HACK, 30, ExternalDamage.SourceKind.NONE));
                    rt.damage(new ExternalDamage(
                            state.enemy, ExternalDamage.Kind.HACK, 60, ExternalDamage.SourceKind.NONE));
                    a.poll();
                    b.poll();
                    a.capture().clear();
                    b.capture().clear();
                })
                .thenExecuteAfter(8, () -> {
                    List<Integer> toA = hudIndices(a);
                    List<Integer> toB = hudIndices(b);
                    replication.visibility(rt.id(), Visibility.ALL);
                    check(toA.contains(Handle.index(state.own)), "faction 0 lacks its own unit's hit points: " + toA);
                    check(!toA.contains(Handle.index(state.enemy)), "faction 0 got the hidden enemy's: " + toA);
                    check(
                            toB.contains(Handle.index(state.enemy)) && toB.contains(Handle.index(state.own)),
                            "faction 1, seeing both, got " + toB);
                    a.close();
                    b.close();
                })
                .thenSucceed();
    }

    private static List<Integer> hudIndices(MockViewer viewer) {
        viewer.poll();
        List<Integer> indices = new ArrayList<>();
        for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (p.payload() instanceof UnitHudPayload hud) {
                hud.hud().units().forEach(e -> indices.add(e.index()));
            }
        }
        return indices;
    }

    private static MockViewer seated(GameTestHelper h, MatchRuntime rt, String name, int slot) {
        Vec3 at = h.absoluteVec(new Vec3(8.5, 20, 8.5));
        MockViewer viewer = MockViewer.join(h.getLevel(), name, at.x, at.y, at.z, 4);
        StrategosServer.get().matches().join(rt, viewer.player().getUUID(), new PlayerSlot(slot));
        StrategosServer.get().net().joined(rt, viewer.player(), new PlayerSlot(slot));
        return viewer;
    }

    private static void collect(MockViewer viewer, List<UnitSummary> into, MatchRuntime rt) {
        viewer.poll();
        for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
            if (p.payload() instanceof UnitSummaryPayload s
                    && s.summary().matchId() == rt.id().value()) {
                into.add(s.summary());
            }
        }
    }
}
