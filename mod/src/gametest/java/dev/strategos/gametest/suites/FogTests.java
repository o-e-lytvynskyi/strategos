package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.RuleChange;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.vision.VisionGrid;
import dev.strategos.core.sim.vision.VisionSystem;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.UnitSummaryPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * Fog of war GameTests (FOW-07, UI-16, MATCH-07): pairing and {@code unit_summary} respect the visibility index. The
 * viewer stands at match-local (4, 4) with a view distance of 2 chunks, so vanilla tracks every body within 32 blocks
 * of it: a "hidden" enemy stands at (24, 4), 20 blocks off, outside faction 0's 12-block vision but inside the tracking
 * range, so only the fog gate keeps it from the viewer.
 */
public final class FogTests {
    private static final FactionSlot FACTION_0 = new FactionSlot(0);
    private static final FactionSlot FACTION_1 = new FactionSlot(1);
    private static final Vec2d OWN = new Vec2d(4, 4);
    private static final Vec2d IN_VISION = new Vec2d(10, 4);
    private static final Vec2d OUT_OF_VISION = new Vec2d(24, 4);
    /** FOW-07.3's limits, in ticks. */
    private static final int PAIRS_WITHIN = 6;

    private static final int UNPAIRS_WITHIN = 15;

    private FogTests() {}

    private static MatchRuntime fogMatch(GameTestHelper h) {
        return MatchFixture.create(h, MatchFixture.fogOptions(SimHarness.DEFAULT_SEED, h));
    }

    private static void spawn(MatchRuntime rt, FactionSlot faction, Vec2d at) {
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, faction, at, 1, 0));
    }

    private static MockViewer viewerAtOwn(ServerLevel level, MatchRuntime rt, String name) {
        MockViewer viewer = MockViewer.join(
                level,
                name,
                rt.space().origin().getX() + OWN.x(),
                rt.space().origin().getY() + 2,
                rt.space().origin().getZ() + OWN.z(),
                2);
        NetFixtures.seat(rt, viewer, 0);
        return viewer;
    }

    private static ChunkPos chunkOf(MatchRuntime rt, Vec2d at) {
        return new ChunkPos(BlockPos.containing(
                rt.space().origin().getX() + at.x(), 0, rt.space().origin().getZ() + at.z()));
    }

    /** "Not yet" until the viewer holds the chunks of every given place: sending a chunk pairs nothing by itself. */
    private static void chunksSent(MatchRuntime rt, MockViewer viewer, Vec2d... places) {
        viewer.poll();
        for (Vec2d at : places) {
            check(viewer.chunks().contains(chunkOf(rt, at)), "the chunk at " + at + " is not sent yet");
        }
    }

    /** The faction-1 unit nearest match-local {@code at}. */
    private static int enemyNear(MatchRuntime rt, Vec2d at) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int handle : core(rt).queries().unitHandlesOf(FACTION_1)) {
            double d = dist2(core(rt).queries().unit(handle), at.x(), at.z());
            if (d < bestD) {
                bestD = d;
                best = handle;
            }
        }
        check(best >= 0, "no enemy near " + at);
        return best;
    }

    private static int bodyId(MatchRuntime rt, int handle) {
        return present(rt.bodies().get(handle), "unit " + Handle.toString(handle) + " has no body")
                .getId();
    }

    private static Set<Integer> added(MockViewer viewer) {
        Set<Integer> ids = new HashSet<>();
        for (ClientboundAddEntityPacket p : viewer.capture().of(ClientboundAddEntityPacket.class)) {
            ids.add(p.getId());
        }
        return ids;
    }

    private static boolean removed(MockViewer viewer, int id) {
        for (ClientboundRemoveEntitiesPacket p : viewer.capture().of(ClientboundRemoveEntitiesPacket.class)) {
            if (p.getEntityIds().contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static VisionSystem vision(MatchRuntime rt) {
        return present(core(rt).vision(), "no vision");
    }

    /** Whether faction 0's vision covers the cell the unit stands in (VisibilityIndex's test). */
    private static boolean inVisionOf0(MatchRuntime rt, int handle) {
        UnitView unit = present(core(rt).queries().unit(handle), "unit " + Handle.toString(handle) + " is gone");
        return vision(rt).grid(0).count(VisionGrid.cellOf(unit.x()), VisionGrid.cellOf(unit.z())) > 0;
    }

    /**
     * FOW-07.2: an enemy outside vision is never paired to the viewer, though it stands inside vanilla's tracking
     * range, while a visible enemy, the control, is; the viewer's own unit is paired too.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void aHiddenEnemyIsNeverPaired(GameTestHelper h) {
        MatchRuntime rt = fogMatch(h);
        spawn(rt, FACTION_0, OWN);
        spawn(rt, FACTION_1, IN_VISION);
        spawn(rt, FACTION_1, OUT_OF_VISION);
        ServerLevel level = h.getLevel();
        var state = new Object() {
            @Nullable MockViewer viewer;

            int own;
            int visible;
            int hidden;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    int[] own = core(rt).queries().unitHandlesOf(FACTION_0);
                    check(own.length == 1 && core(rt).queries().unitHandlesOf(FACTION_1).length == 2, "not spawned");
                    state.own = own[0];
                    state.visible = enemyNear(rt, IN_VISION);
                    state.hidden = enemyNear(rt, OUT_OF_VISION);
                    bodyId(rt, state.own);
                    bodyId(rt, state.visible);
                    bodyId(rt, state.hidden);
                })
                .thenWaitUntil(() -> {
                    check(vision(rt).visibility(0).isVisible(state.visible), "the near enemy is not visible yet");
                    check(!vision(rt).visibility(0).isVisible(state.hidden), "the far enemy is visible");
                })
                .thenExecute(() -> state.viewer = viewerAtOwn(level, rt, "fogviewer"))
                .thenWaitUntil(() -> chunksSent(rt, viewer(state.viewer), OWN, IN_VISION, OUT_OF_VISION))
                .thenWaitUntil(() -> {
                    MockViewer viewer = viewer(state.viewer);
                    viewer.moved();
                    viewer.poll();
                    Set<Integer> added = added(viewer);
                    check(added.contains(bodyId(rt, state.own)), "own body not paired yet");
                    check(added.contains(bodyId(rt, state.visible)), "the visible enemy not paired yet (control)");
                })
                .thenExecuteAfter(20, () -> {
                    MockViewer viewer = viewer(state.viewer);
                    viewer.moved();
                    viewer.poll();
                    check(
                            !added(viewer).contains(bodyId(rt, state.hidden)),
                            "the hidden enemy was paired with the viewer");
                    check(!vision(rt).visibility(0).isVisible(state.hidden), "the far enemy became visible");
                    viewer.close();
                })
                .thenSucceed();
    }

    /**
     * FOW-07.3: an enemy walking into faction 0's vision is paired within {@value #PAIRS_WITHIN} ticks of the tick its
     * cell is first seen; walking out, it is unpaired within {@value #UNPAIRS_WITHIN} ticks of the first tick its cell
     * is no longer seen (the index's hysteresis, then the tracker refresh).
     */
    @StrategosTest(timeoutTicks = 800)
    public static void pairsWithin6UnpairsWithin15(GameTestHelper h) {
        MatchRuntime rt = fogMatch(h);
        spawn(rt, FACTION_0, OWN);
        spawn(rt, FACTION_1, OUT_OF_VISION);
        ServerLevel level = h.getLevel();
        var state = new Object() {
            @Nullable MockViewer viewer;

            int phase;
            int enemy;
            int enemyId;
            long settleAt;
            long visibleAt = -1;
            long leftAt = -1;
        };
        h.onEachTick(() -> {
            long tick = h.getTick();
            switch (state.phase) {
                case 0 -> {
                    if (core(rt).queries().unitHandlesOf(FACTION_0).length == 1
                            && core(rt).queries().unitHandlesOf(FACTION_1).length == 1
                            && rt.bodies().get(core(rt).queries().unitHandlesOf(FACTION_1)[0]) != null) {
                        state.enemy = core(rt).queries().unitHandlesOf(FACTION_1)[0];
                        state.enemyId = bodyId(rt, state.enemy);
                        state.viewer = viewerAtOwn(level, rt, "fogpairing");
                        state.settleAt = tick + 40;
                        state.phase = 1;
                    }
                }
                case 1 -> {
                    MockViewer viewer = viewer(state.viewer);
                    viewer.poll();
                    if (tick % 20 == 0) {
                        viewer.moved();
                    }
                    if (tick >= state.settleAt
                            && viewer.chunks().contains(chunkOf(rt, OWN))
                            && viewer.chunks().contains(chunkOf(rt, IN_VISION))
                            && viewer.chunks().contains(chunkOf(rt, OUT_OF_VISION))) {
                        viewer.moved();
                        viewer.poll();
                        check(!added(viewer).contains(state.enemyId), "the enemy was paired before it was seen");
                        rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(state.enemy), IN_VISION));
                        state.phase = 2;
                    }
                }
                case 2 -> {
                    MockViewer viewer = viewer(state.viewer);
                    viewer.poll();
                    if (state.visibleAt < 0 && inVisionOf0(rt, state.enemy)) {
                        state.visibleAt = tick;
                    }
                    if (added(viewer).contains(state.enemyId)) {
                        check(state.visibleAt >= 0, "the enemy was paired before its cell was seen");
                        check(
                                tick - state.visibleAt <= PAIRS_WITHIN,
                                "paired " + (tick - state.visibleAt) + " ticks after entering vision");
                        rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(state.enemy), OUT_OF_VISION));
                        state.phase = 3;
                    }
                }
                case 3 -> {
                    MockViewer viewer = viewer(state.viewer);
                    viewer.poll();
                    if (state.leftAt < 0 && !inVisionOf0(rt, state.enemy)) {
                        state.leftAt = tick;
                    }
                    if (removed(viewer, state.enemyId)) {
                        check(state.leftAt >= 0, "the enemy was unpaired while still in vision");
                        check(
                                tick - state.leftAt <= UNPAIRS_WITHIN,
                                "unpaired " + (tick - state.leftAt) + " ticks after leaving vision");
                        viewer.close();
                        state.phase = 4;
                        h.succeed();
                    }
                }
                default -> {}
            }
        });
    }

    /**
     * UI-16.2: {@code unit_summary} (minimap dot data) holds the viewer's own unit and a visible enemy, never a fogged
     * enemy.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void noDotDataForFoggedUnits(GameTestHelper h) {
        MatchRuntime rt = fogMatch(h);
        spawn(rt, FACTION_0, OWN);
        spawn(rt, FACTION_1, IN_VISION);
        spawn(rt, FACTION_1, OUT_OF_VISION);
        ServerLevel level = h.getLevel();
        var state = new Object() {
            @Nullable MockViewer viewer;

            int own;
            int visible;
            int fogged;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    int[] own = core(rt).queries().unitHandlesOf(FACTION_0);
                    check(own.length == 1 && core(rt).queries().unitHandlesOf(FACTION_1).length == 2, "not spawned");
                    state.own = own[0];
                    state.visible = enemyNear(rt, IN_VISION);
                    state.fogged = enemyNear(rt, OUT_OF_VISION);
                })
                .thenWaitUntil(() -> {
                    check(vision(rt).visibility(0).isVisible(state.visible), "near enemy not yet visible");
                    check(!vision(rt).visibility(0).isVisible(state.fogged), "far enemy should stay fogged");
                })
                .thenExecute(() -> state.viewer = viewerAtOwn(level, rt, "fogdots"))
                .thenExecuteAfter(40, () -> {
                    MockViewer viewer = viewer(state.viewer);
                    viewer.poll();
                    viewer.capture().clear();
                })
                .thenExecuteAfter(20, () -> {
                    MockViewer viewer = viewer(state.viewer);
                    viewer.poll();
                    List<UnitSummary> frames = new ArrayList<>();
                    for (ClientboundCustomPayloadPacket p : viewer.capture().of(ClientboundCustomPayloadPacket.class)) {
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
                    viewer.close();
                })
                .thenSucceed();
    }

    /**
     * MATCH-07.2: with {@code revealMap}, FogGate shows every body to every participant — the enemy outside faction 0's
     * vision included.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void aRevealedMapShowsEverything(GameTestHelper h) {
        MatchRuntime rt = fogMatch(h);
        rt.submit(PlayerSlot.ADMIN, new RuleChange("revealMap", "true"));
        spawn(rt, FACTION_0, OWN);
        spawn(rt, FACTION_1, OUT_OF_VISION);
        ServerLevel level = h.getLevel();
        var state = new Object() {
            @Nullable MockViewer viewer;
        };
        h.startSequence()
                .thenWaitUntil(() -> {
                    int[] own = core(rt).queries().unitHandlesOf(FACTION_0);
                    int[] enemy = core(rt).queries().unitHandlesOf(FACTION_1);
                    check(own.length == 1 && enemy.length == 1, "not spawned yet");
                    check(rt.bodies().get(own[0]) != null && rt.bodies().get(enemy[0]) != null, "no bodies yet");
                    check(core(rt).queries().config().rules().play().revealMap(), "revealMap not on");
                })
                .thenExecute(() -> state.viewer = viewerAtOwn(level, rt, "revealed"))
                .thenWaitUntil(() -> chunksSent(rt, viewer(state.viewer), OWN, OUT_OF_VISION))
                .thenWaitUntil(() -> {
                    MockViewer viewer = viewer(state.viewer);
                    viewer.moved();
                    viewer.poll();
                    int own = core(rt).queries().unitHandlesOf(FACTION_0)[0];
                    int enemy = core(rt).queries().unitHandlesOf(FACTION_1)[0];
                    Set<Integer> added = added(viewer);
                    check(added.contains(bodyId(rt, own)), "own body not paired yet under revealMap");
                    check(added.contains(bodyId(rt, enemy)), "enemy body not paired yet under revealMap");
                })
                .thenExecute(() -> {
                    int enemy = core(rt).queries().unitHandlesOf(FACTION_1)[0];
                    check(vision(rt).visibility(0).isVisible(enemy), "revealMap should make the enemy visible");
                    viewer(state.viewer).close();
                })
                .thenSucceed();
    }

    private static MockViewer viewer(@Nullable MockViewer viewer) {
        return present(viewer, "no viewer yet");
    }

    private static <T> T present(@Nullable T value, String failure) {
        if (value == null) {
            throw new GameTestAssertException(failure);
        }
        return value;
    }

    private static double dist2(@Nullable UnitView view, double x, double z) {
        UnitView unit = present(view, "unit missing");
        double dx = unit.x() - x;
        double dz = unit.z() - z;
        return dx * dx + dz * dz;
    }
}
