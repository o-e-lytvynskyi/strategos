package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.payload.BuildingStatePayload;
import dev.strategos.mod.server.net.payload.DepositStatePayload;
import dev.strategos.mod.server.net.payload.FactionStatePayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.UiQueryPayload;
import dev.strategos.mod.server.net.payload.UnitDetailPayload;
import dev.strategos.mod.server.net.payload.VisionDeltaPayload;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/** Full joins are private-view boundaries even when the UUID, match and primary remain identical. */
public final class RecipientRejoinTests {
    private RecipientRejoinTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void fullJoinRenewsTheViewAndResendsStablePrivateSnapshotsWithoutOldQueries(GameTestHelper h) {
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
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(6, 6), 1, 0));
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(6, 18), 1, 0));
        var state = new Object() {
            long generation;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready()
                                && core(rt).queries().unitHandlesOf(new FactionSlot(0)).length == 1
                                && core(rt).queries().unitHandlesOf(new FactionSlot(1)).length == 1,
                        "units not ready"))
                .thenExecute(() -> kit.admit("rejoin", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenExecute(() -> {
                    query(kit, 0);
                    query(kit, 1);
                })
                .thenWaitUntil(() -> check(initial(kit, 0) && initial(kit, 1), "initial private views not established"))
                .thenExecute(() -> {
                    kit.viewer(0).poll();
                    kit.viewer(1).poll();
                    kit.viewer(0).capture().clear();
                    kit.viewer(1).capture().clear();
                    state.generation =
                            rt.roster().generationOf(kit.viewer(0).player().getUUID());
                    marker(kit, 1, 700001);
                    marker(kit, 0, 700002);
                    server.net().joined(rt, kit.viewer(0).player(), new PlayerSlot(0));
                    marker(kit, 0, 700003);
                    for (int peer = 0; peer < 2; peer++) {
                        int handle = core(rt).queries().unitHandlesOf(new FactionSlot(peer))[0];
                        rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(handle), new Vec2d(24, 6 + 12 * peer)));
                    }
                })
                .thenWaitUntil(() -> check(
                        received(kit, 0, 700003) && received(kit, 1, 700001),
                        "new view or unaffected peer lost a positive frame"))
                .thenExecute(() -> {
                    check(
                            rt.roster().generationOf(kit.viewer(0).player().getUUID()) > state.generation,
                            "full join reused a live recipient generation");
                    check(!received(kit, 0, 700002), "pre-join private packet survived the full-join boundary");
                })
                .thenWaitUntil(() -> check(
                        snapshots(kit, 0) && changedDetail(kit, 1),
                        "stable private snapshots/full vision or unaffected peer query not delivered"))
                .thenExecute(() -> {
                    check(
                            kit.payloads(0, UnitDetailPayload.class).isEmpty(),
                            "previous view's query subscription survived the full join");
                    query(kit, 0);
                })
                .thenWaitUntil(
                        () -> check(changedDetail(kit, 0), "fresh query did not resume details for the new view"))
                .thenExecute(kit::close)
                .thenSucceed();
    }

    private static boolean initial(MultiplayerHarness kit, int peer) {
        return snapshots(kit, peer)
                && kit.payloads(peer, UnitDetailPayload.class).stream()
                        .anyMatch(frame -> !frame.detail().units().isEmpty());
    }

    private static boolean snapshots(MultiplayerHarness kit, int peer) {
        return !kit.payloads(peer, FactionStatePayload.class).isEmpty()
                && !kit.payloads(peer, BuildingStatePayload.class).isEmpty()
                && !kit.payloads(peer, DepositStatePayload.class).isEmpty()
                && kit.payloads(peer, VisionDeltaPayload.class).stream()
                        .anyMatch(frame -> frame.delta().full());
    }

    private static boolean changedDetail(MultiplayerHarness kit, int peer) {
        return kit.payloads(peer, UnitDetailPayload.class).stream()
                .flatMap(frame -> frame.detail().units().stream())
                .anyMatch(unit -> !unit.orders().isEmpty());
    }

    private static void query(MultiplayerHarness kit, int peer) {
        int handle = core(kit.match()).queries().unitHandlesOf(new FactionSlot(peer))[0];
        kit.viewer(peer).send(new UiQueryPayload(new UiQuery(List.of(handle))));
    }

    private static void marker(MultiplayerHarness kit, int peer, int sequence) {
        StrategosServer.get()
                .net()
                .send(
                        new Audience.Player(kit.viewer(peer).player()),
                        new FeedbackPayload(new Feedback(sequence, CommandOutcome.ACCEPTED, null, 1, 1)));
    }

    private static boolean received(MultiplayerHarness kit, int peer, int sequence) {
        return kit.payloads(peer, FeedbackPayload.class).stream()
                .anyMatch(frame -> frame.message() instanceof Feedback reply && reply.clientSeq() == sequence);
    }
}
