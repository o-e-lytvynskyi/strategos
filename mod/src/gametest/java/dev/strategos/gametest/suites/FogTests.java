package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.vision.VisionSystem;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.UnitSummaryPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;

/**
 * Fog of war GameTests (FOW-07, UI-16): pairing and {@code unit_summary} respect the visibility index.
 */
public final class FogTests {
    private static final FactionSlot FACTION_0 = new FactionSlot(0);
    private static final FactionSlot FACTION_1 = new FactionSlot(1);

    private FogTests() {}

    /**
     * FOW-07.2: an enemy unit that was never in vision is never paired with the viewer. Faction 0 at (4, 4), faction 1
     * at (28, 28), default vision 12 blocks — they never see each other. The viewer seated as faction 0 must receive
     * its own faction's body but never faction 1's.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void aHiddenEnemyIsNeverPaired(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, new Vec2d(4, 4), 1, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_1, new Vec2d(28, 28), 1, 0));
        ServerLevel level = h.getLevel();
        var state = new Object() {
            MockViewer viewer;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    int[] own = core(rt).queries().unitHandlesOf(FACTION_0);
                    int[] enemy = core(rt).queries().unitHandlesOf(FACTION_1);
                    check(own.length == 1 && enemy.length == 1, "not spawned yet");
                    check(rt.bodies().get(own[0]) != null && rt.bodies().get(enemy[0]) != null, "no bodies yet");
                })
                .thenExecute(() -> {
                    state.viewer = MockViewer.join(
                            level,
                            "fogviewer",
                            rt.space().origin().getX() + 4.0,
                            rt.space().origin().getY() + 2,
                            rt.space().origin().getZ() + 4.0,
                            2);
                    NetFixtures.seat(rt, state.viewer, 0);
                })
                .thenExecuteAfter(40, () -> {
                    state.viewer.poll();
                    state.viewer.moved();
                })
                .thenExecuteAfter(20, () -> {
                    state.viewer.poll();
                    int[] enemy = core(rt).queries().unitHandlesOf(FACTION_1);
                    UnitEntity enemyBody = rt.bodies().get(enemy[0]);
                    check(enemyBody != null, "enemy body missing");
                    int enemyId = enemyBody.getId();
                    Set<Integer> added = new HashSet<>();
                    for (ClientboundAddEntityPacket p : state.viewer.capture().of(ClientboundAddEntityPacket.class)) {
                        added.add(p.getId());
                    }
                    check(!added.contains(enemyId), "the hidden enemy was paired with the viewer");
                    int[] own = core(rt).queries().unitHandlesOf(FACTION_0);
                    UnitEntity ownBody = rt.bodies().get(own[0]);
                    check(ownBody != null && added.contains(ownBody.getId()), "own body was not paired");
                    VisionSystem vision = core(rt).vision();
                    check(vision != null, "no vision");
                    check(!vision.visibility(0).isVisible(enemy[0]), "the enemy should not be visible to faction 0");
                    state.viewer.close();
                })
                .thenSucceed();
    }

    /**
     * UI-16.2: {@code unit_summary} (minimap dot data) holds the viewer's own unit and a visible enemy, never a fogged
     * enemy. Faction 0 at (4, 4); faction 1 visible at (10, 4) and fogged at (28, 28); default vision 12 blocks.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void noDotDataForFoggedUnits(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, new Vec2d(4, 4), 1, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_1, new Vec2d(10, 4), 1, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_1, new Vec2d(28, 28), 1, 0));
        ServerLevel level = h.getLevel();
        var state = new Object() {
            MockViewer viewer;
            int own;
            int visible;
            int fogged;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    int[] own = core(rt).queries().unitHandlesOf(FACTION_0);
                    int[] enemy = core(rt).queries().unitHandlesOf(FACTION_1);
                    check(own.length == 1 && enemy.length == 2, "not spawned yet");
                    state.own = own[0];
                    double d0 = dist2(core(rt).queries().unit(enemy[0]), 10, 4);
                    double d1 = dist2(core(rt).queries().unit(enemy[1]), 10, 4);
                    state.visible = d0 <= d1 ? enemy[0] : enemy[1];
                    state.fogged = state.visible == enemy[0] ? enemy[1] : enemy[0];
                })
                .thenWaitUntil(() -> {
                    VisionSystem vision = core(rt).vision();
                    check(vision != null, "no vision");
                    check(vision.visibility(0).isVisible(state.visible), "near enemy not yet visible");
                    check(!vision.visibility(0).isVisible(state.fogged), "far enemy should stay fogged");
                })
                .thenExecute(() -> {
                    state.viewer = MockViewer.join(
                            level,
                            "fogdots",
                            rt.space().origin().getX() + 4.0,
                            rt.space().origin().getY() + 2,
                            rt.space().origin().getZ() + 4.0,
                            2);
                    NetFixtures.seat(rt, state.viewer, 0);
                })
                .thenExecuteAfter(40, () -> {
                    state.viewer.poll();
                    state.viewer.capture().clear();
                })
                .thenExecuteAfter(20, () -> {
                    state.viewer.poll();
                    List<UnitSummary> frames = new ArrayList<>();
                    for (ClientboundCustomPayloadPacket p :
                            state.viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
                        if (p.payload() instanceof UnitSummaryPayload s
                                && s.summary().matchId() == rt.id().value()) {
                            frames.add(s.summary());
                        }
                    }
                    check(!frames.isEmpty(), "no unit_summary reached the viewer");
                    for (UnitSummary frame : frames) {
                        Set<Integer> indices = new HashSet<>();
                        frame.units().forEach(e -> indices.add(e.index()));
                        check(indices.contains(Handle.index(state.own)), "own unit missing from summary");
                        check(
                                indices.contains(Handle.index(state.visible)),
                                "visible enemy missing from summary (positive control)");
                        check(
                                !indices.contains(Handle.index(state.fogged)),
                                "fogged enemy reached the client as dot data");
                        check(indices.size() == 2, "summary holds " + indices.size() + " units, not 2");
                    }
                    state.viewer.close();
                })
                .thenSucceed();
    }

    private static double dist2(dev.strategos.core.sim.UnitView unit, double x, double z) {
        check(unit != null, "unit missing");
        double dx = unit.x() - x;
        double dz = unit.z() - z;
        return dx * dx + dz * dz;
    }

    /**
     * MATCH-07.2: with {@code revealMap}, FogGate shows every body to every participant — even an enemy that would be
     * hidden under fog (faction 0 at (4, 4), faction 1 at (28, 28)).
     */
    @StrategosTest(timeoutTicks = 200)
    public static void aRevealedMapShowsEverything(GameTestHelper h) {
        MatchRuntime rt = Units.match(h);
        rt.submit(PlayerSlot.ADMIN, new RuleChange("revealMap", "true"));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_0, new Vec2d(4, 4), 1, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, FACTION_1, new Vec2d(28, 28), 1, 0));
        ServerLevel level = h.getLevel();
        var state = new Object() {
            MockViewer viewer;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    int[] own = core(rt).queries().unitHandlesOf(FACTION_0);
                    int[] enemy = core(rt).queries().unitHandlesOf(FACTION_1);
                    check(own.length == 1 && enemy.length == 1, "not spawned yet");
                    check(rt.bodies().get(own[0]) != null && rt.bodies().get(enemy[0]) != null, "no bodies yet");
                    check(core(rt).queries().config().rules().play().revealMap(), "revealMap not on");
                })
                .thenExecute(() -> {
                    state.viewer = MockViewer.join(
                            level,
                            "revealed",
                            rt.space().origin().getX() + 4.0,
                            rt.space().origin().getY() + 2,
                            rt.space().origin().getZ() + 4.0,
                            2);
                    NetFixtures.seat(rt, state.viewer, 0);
                })
                .thenExecuteAfter(40, () -> {
                    state.viewer.poll();
                    state.viewer.moved();
                })
                .thenExecuteAfter(20, () -> {
                    state.viewer.poll();
                    int[] own = core(rt).queries().unitHandlesOf(FACTION_0);
                    int[] enemy = core(rt).queries().unitHandlesOf(FACTION_1);
                    UnitEntity ownBody = rt.bodies().get(own[0]);
                    UnitEntity enemyBody = rt.bodies().get(enemy[0]);
                    check(ownBody != null && enemyBody != null, "bodies missing");
                    Set<Integer> added = new HashSet<>();
                    for (ClientboundAddEntityPacket p : state.viewer.capture().of(ClientboundAddEntityPacket.class)) {
                        added.add(p.getId());
                    }
                    check(added.contains(ownBody.getId()), "own body was not paired under revealMap");
                    check(added.contains(enemyBody.getId()), "enemy body was not paired under revealMap");
                    VisionSystem vision = core(rt).vision();
                    check(vision != null, "no vision");
                    check(vision.visibility(0).isVisible(enemy[0]), "revealMap should make the enemy visible");
                    state.viewer.close();
                })
                .thenSucceed();
    }
}
