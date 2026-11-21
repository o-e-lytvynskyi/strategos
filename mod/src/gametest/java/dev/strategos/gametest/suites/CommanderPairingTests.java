package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.DiplomacySetup;
import dev.strategos.core.sim.diplomacy.DiplomacyState;
import dev.strategos.core.sim.vision.VisionGrid;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.level.GameType;

/** Real tracker transitions, including fog changes that do not change any core-unit visibility bit. */
public final class CommanderPairingTests {
    private CommanderPairingTests() {}

    private static MatchRuntime match(GameTestHelper h) {
        var rt = MatchFixture.create(h, MatchFixture.fogOptions(SimHarness.DEFAULT_SEED, h));
        rt.initializeDiplomacy(new DiplomacySetup(DiplomacyState.identity(2)
                .bind(new PlayerSlot(2), new FactionSlot(0))
                .lockTeams()
                .snapshot()));
        spawn(rt, 0, 4, 4);
        return rt;
    }

    private static void spawn(MatchRuntime rt, int faction, double x, double z) {
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(faction), new Vec2d(x, z), 1, 0));
    }

    private static MockViewer join(GameTestHelper h, MatchRuntime rt, String name, int slot, double x, double z) {
        var at = rt.space().origin();
        var viewer = MockViewer.join(h.getLevel(), name, at.getX() + x, at.getY() + 2, at.getZ() + z, 2);
        viewer.player().setGameMode(GameType.ADVENTURE);
        NetFixtures.seat(rt, viewer, slot);
        StrategosServer.get().net().flush();
        h.onEachTick(viewer::poll); // Deliberately no periodic moved(): the production refresh must do its job.
        return viewer;
    }

    private static boolean added(MockViewer viewer, int id) {
        viewer.poll();
        return viewer.capture().of(ClientboundAddEntityPacket.class).stream().anyMatch(p -> p.getId() == id);
    }

    private static boolean removed(MockViewer viewer, int id) {
        viewer.poll();
        return viewer.capture().of(ClientboundRemoveEntitiesPacket.class).stream()
                .anyMatch(p -> p.getEntityIds().contains(id));
    }

    private static boolean lit(MatchRuntime rt, double x, double z) {
        var vision = core(rt).vision();
        check(vision != null, "fixture has no vision system");
        return java.util.Objects.requireNonNull(vision).grid(0).count(VisionGrid.cellOf(x), VisionGrid.cellOf(z)) > 0;
    }

    @StrategosTest(timeoutTicks = 240)
    public static void visibleEnemyBodyIsRemovedWhenItEntersCommanderModeButCoopCommanderRemains(GameTestHelper h) {
        var rt = match(h);
        var viewers = new java.util.ArrayList<MockViewer>();
        h.startSequence()
                .thenWaitUntil(
                        () -> check(core(rt).queries().tick() > 0 && lit(rt, 10, 4), "waiting for initial vision"))
                .thenExecute(() -> {
                    viewers.add(join(h, rt, "pair-mode-0", 0, 4, 4));
                    viewers.add(join(h, rt, "pair-mode-1", 1, 10, 4));
                    viewers.add(join(h, rt, "pair-mode-2", 2, 6, 4));
                    viewers.get(0).player().setGameMode(GameType.SPECTATOR);
                })
                .thenWaitUntil(() -> {
                    var viewer = viewers.get(0);
                    viewer.moved();
                    check(added(viewer, viewers.get(1).player().getId()), "visible enemy body positive not paired");
                    check(added(viewer, viewers.get(2).player().getId()), "co-op body positive not paired");
                })
                .thenExecute(() -> {
                    viewers.get(0).capture().clear();
                    viewers.get(1).player().setGameMode(GameType.SPECTATOR);
                    viewers.get(2).player().setGameMode(GameType.SPECTATOR);
                    viewers.get(0).moved();
                })
                .thenExecuteAfter(10, () -> {
                    var viewer = viewers.get(0);
                    check(removed(viewer, viewers.get(1).player().getId()), "enemy commander stayed paired");
                    check(!added(viewer, viewers.get(1).player().getId()), "enemy commander was paired again");
                    check(!removed(viewer, viewers.get(2).player().getId()), "same-primary co-op commander was hidden");
                    for (var peer : viewers) peer.close();
                    StrategosServer.get().matches().dispose(rt.id());
                })
                .thenSucceed();
    }

    @StrategosTest(timeoutTicks = 500)
    public static void stationaryBodyFollowsCurrentFogEvenWhenTheUnitVisibilityVersionDoesNotChange(GameTestHelper h) {
        var rt = match(h);
        var viewers = new java.util.ArrayList<MockViewer>();
        int[] own = {0}, version = {0};
        h.startSequence()
                .thenWaitUntil(() -> {
                    check(core(rt).queries().tick() > 0 && lit(rt, 10, 4), "waiting for initial vision");
                    own[0] = core(rt).queries().unitHandlesOf(new FactionSlot(0))[0];
                })
                .thenExecute(() -> {
                    viewers.add(join(h, rt, "pair-fog-2", 2, 4, 4));
                    viewers.add(join(h, rt, "pair-fog-1", 1, 10, 4));
                    viewers.get(0).player().setGameMode(GameType.SPECTATOR);
                })
                .thenWaitUntil(() -> {
                    viewers.get(0).moved();
                    check(
                            added(viewers.get(0), viewers.get(1).player().getId()),
                            "initial visible body positive not paired");
                })
                .thenExecute(() -> {
                    version[0] = java.util.Objects.requireNonNull(core(rt).vision())
                            .visibility(0)
                            .version();
                    viewers.get(0).capture().clear();
                    rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(own[0]), new Vec2d(4, 24)));
                })
                .thenWaitUntil(() -> check(!lit(rt, 10, 4), "waiting for cell to leave current vision"))
                .thenExecuteAfter(15, () -> {
                    var vision = java.util.Objects.requireNonNull(core(rt).vision());
                    check(
                            vision.visibility(0).version() == version[0],
                            "unit visibility changed and masked the player-body defect");
                    check(
                            vision.isExplored(0, VisionGrid.cellOf(10), VisionGrid.cellOf(4)),
                            "former visible cell lost exploration");
                    check(
                            removed(viewers.get(0), viewers.get(1).player().getId()),
                            "stationary enemy body leaked through explored fog");
                    viewers.get(0).capture().clear();
                    rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(own[0]), new Vec2d(4, 4)));
                })
                .thenWaitUntil(() -> check(lit(rt, 10, 4), "waiting for current vision to return"))
                .thenExecuteAfter(15, () -> {
                    check(
                            java.util.Objects.requireNonNull(core(rt).vision())
                                            .visibility(0)
                                            .version()
                                    == version[0],
                            "unit version changed on return");
                    check(
                            added(viewers.get(0), viewers.get(1).player().getId()),
                            "stationary visible body was not paired again");
                    for (var peer : viewers) peer.close();
                    StrategosServer.get().matches().dispose(rt.id());
                })
                .thenSucceed();
    }
}
