package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.net.payload.BuildingStatePayload;
import dev.strategos.mod.server.net.payload.DepositStatePayload;
import dev.strategos.mod.server.net.payload.FactionStatePayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.MatchPresentationPayload;
import dev.strategos.mod.server.net.payload.MatchStatePayload;
import dev.strategos.mod.server.net.payload.PlayerAuthorityPayload;
import dev.strategos.mod.server.net.payload.UiQueryPayload;
import dev.strategos.mod.server.net.payload.UnitDetailPayload;
import dev.strategos.mod.server.net.payload.UnitHudPayload;
import dev.strategos.mod.server.net.payload.UnitSummaryPayload;
import dev.strategos.mod.server.net.payload.VisionDeltaPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;

/** Entity spectator flags confer no match authority; only the server's explicit observer seat does. */
public final class RecipientAccessTests {
    private RecipientAccessTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void aSpectatorEntityCannotReadMatchStateUntilExplicitlyAdmittedAsAnObserver(GameTestHelper h) {
        var options = new MatchOptions(
                SimHarness.DEFAULT_SEED,
                2,
                false,
                MatchSource.GAMETEST,
                null,
                MetricsSink.none(),
                Math.floor(h.getBounds().minY) + 1,
                null,
                MatchOptions.GAMETEST_PLAY);
        MultiplayerHarness kit = MultiplayerHarness.create(h, options);
        var rt = kit.match();
        var server = StrategosServer.get();
        var at = h.absolutePos(new BlockPos(12, 20, 12));
        MockViewer spectator = MockViewer.join(h.getLevel(), "view-spectator", at.getX(), at.getY(), at.getZ(), 2);
        check(spectator.player().setGameMode(GameType.SPECTATOR), "spectator mode was not applied");
        h.onEachTick(spectator::poll);
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(6, 6), 1, 0));
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(6, 18), 1, 0));
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready()
                                && core(rt).queries().unitHandlesOf(new FactionSlot(0)).length == 1
                                && core(rt).queries().unitHandlesOf(new FactionSlot(1)).length == 1,
                        "units not ready"))
                .thenExecute(() -> kit.admit("access", List.of(new PlayerSlot(0))))
                .thenExecute(() -> {
                    UiQuery request = query(rt);
                    spectator.send(new UiQueryPayload(request));
                    kit.viewer(0).send(new UiQueryPayload(request));
                })
                .thenWaitUntil(() -> check(
                        !kit.payloads(0, FactionStatePayload.class).isEmpty()
                                && kit.payloads(0, UnitDetailPayload.class).stream()
                                        .anyMatch(
                                                frame -> frame.detail().units().size() == 1),
                        "owner private snapshot/query positive control missing"))
                .thenExecute(() -> {
                    int own = core(rt).queries().unitHandlesOf(new FactionSlot(0))[0];
                    check(
                            kit.payloads(0, UnitDetailPayload.class).stream()
                                    .flatMap(frame -> frame.detail().units().stream())
                                    .allMatch(unit -> unit.handle() == own),
                            "owner query exposed an enemy's private details");
                    check(noMatchFrames(spectator), "unbound spectator received a match projection");
                    server.matches().join(rt, spectator.player().getUUID(), PlayerSlot.ADMIN);
                    server.net().joined(rt, spectator.player(), PlayerSlot.ADMIN);
                    spectator.send(new UiQueryPayload(query(rt)));
                })
                .thenWaitUntil(() -> check(
                        !NetFixtures.payloads(spectator, MatchJoinPayload.class).isEmpty()
                                && NetFixtures.payloads(spectator, UnitDetailPayload.class).stream()
                                        .anyMatch(
                                                frame -> frame.detail().units().size() == 2),
                        "server-authorized observer did not receive its permitted query"))
                .thenExecute(() -> check(
                        NetFixtures.payloads(spectator, MatchJoinPayload.class).stream()
                                .allMatch(frame -> frame.join().slot().isAdmin()
                                        && frame.join().primaryFaction() == -1
                                        && !frame.join().controlsFaction(0)
                                        && !frame.join().controlsFaction(1)),
                        "observer acquired a primary faction or command rights"))
                .thenExecute(kit::close)
                .thenExecute(spectator::close)
                .thenSucceed();
    }

    private static UiQuery query(MatchRuntime rt) {
        return new UiQuery(List.of(
                core(rt).queries().unitHandlesOf(new FactionSlot(0))[0],
                core(rt).queries().unitHandlesOf(new FactionSlot(1))[0]));
    }

    private static boolean noMatchFrames(MockViewer viewer) {
        return NetFixtures.payloads(viewer, MatchJoinPayload.class).isEmpty()
                && NetFixtures.payloads(viewer, MatchPresentationPayload.class).isEmpty()
                && NetFixtures.payloads(viewer, MatchStatePayload.class).isEmpty()
                && NetFixtures.payloads(viewer, PlayerAuthorityPayload.class).isEmpty()
                && NetFixtures.payloads(viewer, FactionStatePayload.class).isEmpty()
                && NetFixtures.payloads(viewer, BuildingStatePayload.class).isEmpty()
                && NetFixtures.payloads(viewer, DepositStatePayload.class).isEmpty()
                && NetFixtures.payloads(viewer, VisionDeltaPayload.class).isEmpty()
                && NetFixtures.payloads(viewer, UnitSummaryPayload.class).isEmpty()
                && NetFixtures.payloads(viewer, UnitHudPayload.class).isEmpty()
                && NetFixtures.payloads(viewer, UnitDetailPayload.class).isEmpty();
    }
}
