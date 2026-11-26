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
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.payload.FactionStatePayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.UiQueryPayload;
import dev.strategos.mod.server.net.payload.UnitDetailPayload;
import dev.strategos.mod.server.net.payload.UnitSummaryPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** Match-local handle reuse and real replacement connections cannot rebind private producers. */
public final class RecipientTransferTests {
    private RecipientTransferTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void changingMatchesDropsQueuedFramesAndOldQueriesDespiteReusedUnitHandles(GameTestHelper h) {
        MatchRuntime first = MatchFixture.create(h, 0, 0, 20, 48, options(h));
        MatchRuntime second = MatchFixture.create(h, 26, 0, 20, 48, options(h));
        spawn(first);
        spawn(second);
        MockViewer owner = viewer(h, "priv-switch", 8);
        MockViewer peer = viewer(h, "priv-stay", 10);
        h.onEachTick(owner::poll);
        h.onEachTick(peer::poll);
        var server = StrategosServer.get();
        var state = new Object() {
            long movedAt = -1;
        };
        h.startSequence()
                .thenWaitUntil(() -> check(ready(first) && ready(second), "matches not ready"))
                .thenExecute(() -> {
                    check(handle(first) == handle(second), "fixture requires match-local handle reuse");
                    join(first, owner, 0);
                    join(first, peer, 1);
                    query(owner, first);
                })
                .thenWaitUntil(() -> check(
                        !NetFixtures.payloads(owner, UnitDetailPayload.class).isEmpty()
                                && !NetFixtures.payloads(peer, MatchJoinPayload.class)
                                        .isEmpty(),
                        "initial view not established"))
                .thenExecute(() -> {
                    owner.poll();
                    peer.poll();
                    owner.capture().clear();
                    peer.capture().clear();
                    marker(owner, 900001);
                    marker(peer, 900002);
                    join(second, owner, 0);
                    marker(owner, 900003);
                    second.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(handle(second)), new Vec2d(16, 6)));
                })
                .thenWaitUntil(() -> {
                    check(
                            !core(second).queries().orders(handle(second)).isEmpty(),
                            "new match's positive move not applied");
                    state.movedAt = core(second).queries().tick();
                })
                .thenWaitUntil(() -> check(
                        received(owner, 900003)
                                && received(peer, 900002)
                                && NetFixtures.payloads(owner, UnitSummaryPayload.class).stream()
                                        .anyMatch(frame -> frame.summary().matchId()
                                                        == second.id().value()
                                                && frame.summary().tick() >= state.movedAt)
                                && NetFixtures.payloads(owner, FactionStatePayload.class).stream()
                                        .anyMatch(frame -> frame.state().matchId()
                                                == second.id().value()),
                        "new match snapshot or unaffected old-match peer delivery missing"))
                .thenExecute(() -> {
                    check(!received(owner, 900001), "old-match private frame reached the new match");
                    check(
                            NetFixtures.payloads(owner, MatchJoinPayload.class).stream()
                                    .allMatch(frame -> frame.join().matchId()
                                            == second.id().value()),
                            "foreign full join survived");
                    check(
                            NetFixtures.payloads(owner, UnitDetailPayload.class).isEmpty(),
                            "old subscription rebound to a reused handle in the next match");
                    query(owner, second);
                })
                .thenWaitUntil(() -> check(
                        NetFixtures.payloads(owner, UnitDetailPayload.class).stream()
                                .anyMatch(frame ->
                                        frame.detail().matchId() == second.id().value()
                                                && !frame.detail().units().isEmpty()),
                        "fresh query did not work in the new match"))
                .thenExecute(() -> {
                    server.matches().dispose(first.id());
                    server.matches().dispose(second.id());
                    owner.close();
                    peer.close();
                })
                .thenSucceed();
    }

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void aReplacementConnectionWithTheSameUuidNeverReceivesItsPredecessorsFrames(GameTestHelper h) {
        MatchRuntime rt = MatchFixture.create(h, options(h));
        spawn(rt);
        MockViewer original = viewer(h, "priv-relogin", 8);
        MockViewer peer = viewer(h, "priv-peer", 10);
        var state = new Object() {
            MockViewer active = original;
        };
        h.onEachTick(() -> state.active.poll());
        h.onEachTick(peer::poll);
        var server = StrategosServer.get();
        h.startSequence()
                .thenWaitUntil(() -> check(ready(rt), "match not ready"))
                .thenExecute(() -> {
                    join(rt, original, 0);
                    join(rt, peer, 1);
                    query(original, rt);
                })
                .thenWaitUntil(() -> check(
                        !NetFixtures.payloads(original, UnitDetailPayload.class).isEmpty(),
                        "original connection's positive query missing"))
                .thenExecute(() -> {
                    marker(original, 800001);
                    marker(peer, 800004);
                    original.close();
                    MockViewer replacement = viewer(h, "priv-relogin", 8);
                    check(
                            replacement
                                    .player()
                                    .getUUID()
                                    .equals(original.player().getUUID()),
                            "fixture changed the account UUID");
                    state.active = replacement;
                    join(rt, replacement, 0);
                    // An old producer retaining the former ServerPlayer must not be resolved to this new connection.
                    marker(original, 800002);
                    marker(replacement, 800003);
                    query(replacement, rt);
                })
                .thenWaitUntil(() -> check(
                        received(state.active, 800003)
                                && received(peer, 800004)
                                && !NetFixtures.payloads(state.active, MatchJoinPayload.class)
                                        .isEmpty()
                                && !NetFixtures.payloads(state.active, UnitDetailPayload.class)
                                        .isEmpty()
                                && !NetFixtures.payloads(state.active, FactionStatePayload.class)
                                        .isEmpty(),
                        "replacement or unaffected peer lost its positive transport"))
                .thenExecute(() -> {
                    check(
                            !received(state.active, 800001),
                            "queued predecessor frame reached the replacement connection");
                    check(!received(state.active, 800002), "old ServerPlayer producer rebound through UUID lookup");
                    server.matches().dispose(rt.id());
                    state.active.close();
                    original.close();
                    peer.close();
                })
                .thenSucceed();
    }

    private static MatchOptions options(GameTestHelper h) {
        return new MatchOptions(
                SimHarness.DEFAULT_SEED,
                2,
                false,
                MatchSource.GAMETEST,
                null,
                MetricsSink.none(),
                Math.floor(h.getBounds().minY) + 1,
                null,
                MatchOptions.GAMETEST_PLAY);
    }

    private static boolean ready(MatchRuntime rt) {
        var match = rt.core();
        return match != null
                && rt.lifecycle() == MatchLifecycle.RUNNING
                && match.queries().tick() > 0
                && match.queries().unitHandlesOf(new FactionSlot(0)).length == 1;
    }

    private static void spawn(MatchRuntime rt) {
        rt.submit(
                PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(6, 6), 1, 0));
    }

    private static int handle(MatchRuntime rt) {
        return core(rt).queries().unitHandlesOf(new FactionSlot(0))[0];
    }

    private static MockViewer viewer(GameTestHelper h, String name, int x) {
        var at = h.absolutePos(new BlockPos(x, 20, 8));
        return MockViewer.join(h.getLevel(), name, at.getX(), at.getY(), at.getZ(), 2);
    }

    private static void join(MatchRuntime rt, MockViewer viewer, int slot) {
        StrategosServer.get().matches().join(rt, viewer.player().getUUID(), new PlayerSlot(slot));
        StrategosServer.get().net().joined(rt, viewer.player(), new PlayerSlot(slot));
    }

    private static void query(MockViewer viewer, MatchRuntime rt) {
        viewer.send(new UiQueryPayload(new UiQuery(List.of(handle(rt)))));
    }

    private static void marker(MockViewer viewer, int sequence) {
        StrategosServer.get()
                .net()
                .send(
                        new Audience.Player(viewer.player()),
                        new FeedbackPayload(new Feedback(sequence, CommandOutcome.ACCEPTED, null, 1, 1)));
    }

    private static boolean received(MockViewer viewer, int sequence) {
        return NetFixtures.payloads(viewer, FeedbackPayload.class).stream()
                .anyMatch(frame -> frame.message() instanceof Feedback reply && reply.clientSeq() == sequence);
    }
}
