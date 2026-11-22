package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchOptions;
import dev.strategos.mod.server.match.MatchSource;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.UnitSummaryPayload;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.gametest.framework.GameTestHelper;

/** T9.1: private markers use the real queued gateway and separate acknowledging connections. */
public final class RecipientQueueTests {
    private RecipientQueueTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void summaryBackpressureCoalescesRemovalsAndDiscardsAnOldEpoch(GameTestHelper h) {
        var kit = MultiplayerHarness.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var match = kit.match();
        var net = StrategosServer.get().net();
        var own = new FactionSlot(0);
        var other = new FactionSlot(1);
        var entries = IntStream.range(0, UnitSummary.MAX_UNITS)
                .mapToObj(i -> new UnitSummary.Entry(i, 0, 0, 0, 128, 128))
                .toList();
        int matchId = match.id().value();
        var fill = new UnitSummaryPayload(new UnitSummary(matchId, 900_001, entries));
        var pending = new UnitSummaryPayload(new UnitSummary(matchId, 900_002, entries));
        var oldEpoch = new UnitSummaryPayload(new UnitSummary(matchId, 900_005, entries));
        var removed = new UnitSummaryPayload(new UnitSummary(matchId, 900_003, List.of()));
        var unrelated = new UnitSummaryPayload(new UnitSummary(matchId, 900_004, List.of()));
        int[] oldProjections = {0};
        h.startSequence()
                .thenWaitUntil(() -> check(kit.ready(), "summary budget fixture not ready"))
                .thenExecute(() -> kit.admit("sumcap", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenExecute(() -> {
                    for (int i = 0; i < 8; i++) {
                        net.offerSummary(match, own, () -> fill);
                        net.flush();
                    }
                    check(kit.payloads(0, UnitSummaryPayload.class).contains(fill), "no released summary");
                    net.offerSummary(match, own, () -> pending);
                    net.offerSummary(match, other, () -> unrelated);
                    net.flush();
                    check(
                            !kit.payloads(0, UnitSummaryPayload.class).contains(pending),
                            "pressure failed to defer state");
                    check(
                            kit.payloads(1, UnitSummaryPayload.class).contains(unrelated),
                            "pressuring one summary stalled another");
                    net.offerSummary(match, own, () -> removed);
                    net.flush();
                    check(kit.payloads(0, UnitSummaryPayload.class).contains(removed), "new removals did not converge");
                    net.offerSummary(match, own, () -> {
                        oldProjections[0]++;
                        return oldEpoch;
                    });
                    net.flush();
                    check(
                            oldProjections[0] == 1
                                    && !kit.payloads(0, UnitSummaryPayload.class)
                                            .contains(oldEpoch),
                            "old view test did not defer a projected frame");
                    var viewer = kit.viewer(0);
                    long epoch = viewer.epoch();
                    net.joined(match, viewer.player(), new PlayerSlot(0));
                    net.flush();
                    viewer.poll();
                    check(viewer.epoch() != epoch, "full view did not renew the epoch");
                })
                .thenExecuteAfter(20, () -> {
                    check(!kit.payloads(0, UnitSummaryPayload.class).contains(pending), "superseded state resurfaced");
                    check(
                            oldProjections[0] == 1
                                    && !kit.payloads(0, UnitSummaryPayload.class)
                                            .contains(oldEpoch),
                            "deferred old epoch state survived a full view");
                    check(!kit.payloads(1, UnitSummaryPayload.class).contains(fill), "private summary escaped");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void aDeferredSummaryReprojectsAtTheNextEligibleTick(GameTestHelper h) {
        var kit = MultiplayerHarness.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var match = kit.match();
        var server = StrategosServer.get();
        var net = server.net();
        var own = new FactionSlot(0);
        var entries = IntStream.range(0, UnitSummary.MAX_UNITS)
                .mapToObj(i -> new UnitSummary.Entry(i, 0, 0, 0, 128, 128))
                .toList();
        var fill = new UnitSummaryPayload(new UnitSummary(match.id().value(), 900_011, entries));
        var current = new UnitSummaryPayload(new UnitSummary(match.id().value(), 900_012, List.of()));
        UnitSummaryPayload[] live = {current};
        int[] projections = {0};

        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready() && server.server().getTickCount() % 10 == 1,
                        "wait for a non-periodic publication tick"))
                .thenExecute(() -> {
                    kit.admit("sumfresh", List.of(new PlayerSlot(0)));
                    for (int i = 0; i < 8; i++) {
                        net.offerSummary(match, own, () -> fill);
                        net.flush();
                    }
                    var received = kit.payloads(0, UnitSummaryPayload.class);
                    int bytes = received.stream()
                            .mapToInt(p -> WireCodec.encode(UnitSummary.WIRE, p.summary()).length)
                            .sum();
                    int remaining = UnitSummaryPayload.SPEC.cap() - bytes;
                    int low = 1;
                    int high = entries.size();
                    while (low < high) {
                        int middle = (low + high) / 2;
                        var candidate = new UnitSummary(match.id().value(), 900_013, entries.subList(0, middle));
                        if (WireCodec.encode(UnitSummary.WIRE, candidate).length > remaining) high = middle;
                        else low = middle + 1;
                    }
                    live[0] = new UnitSummaryPayload(
                            new UnitSummary(match.id().value(), 900_013, entries.subList(0, low)));
                    net.offerSummary(match, own, () -> {
                        projections[0]++;
                        return live[0];
                    });
                    net.flush();
                    check(
                            projections[0] == 1
                                    && !kit.payloads(0, UnitSummaryPayload.class)
                                            .contains(live[0]),
                            "pending summary unexpectedly fit the current allowance");
                    live[0] = current;
                })
                .thenExecuteAfter(1, () -> {
                    net.flush();
                    check(projections[0] == 2, "pending work did not reproject on the next eligible tick");
                    check(
                            kit.payloads(0, UnitSummaryPayload.class).contains(current),
                            "fresh empty summary was not sent");
                    check(
                            kit.payloads(0, UnitSummaryPayload.class).stream()
                                    .noneMatch(p -> p.summary().tick() == 900_013),
                            "stale projection was sent");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void queuedPrivateFramesCannotSurviveReseatReconnectOrObserverRoleChange(GameTestHelper h) {
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
        h.startSequence()
                .thenWaitUntil(() -> check(kit.ready(), "fixture not ready"))
                .thenExecute(() -> kit.admit("epoch", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenWaitUntil(() -> check(
                        !kit.payloads(0, MatchJoinPayload.class).isEmpty()
                                && !kit.payloads(1, MatchJoinPayload.class).isEmpty(),
                        "sequential admissions lost an initial frame"))
                .thenExecute(() -> {
                    marker(kit, 1, 101);
                    marker(kit, 0, 102);
                    server.matches().join(rt, kit.viewer(0).player().getUUID(), new PlayerSlot(0));
                    marker(kit, 0, 103);
                })
                .thenWaitUntil(() -> check(
                        received(kit, 0, 103) && received(kit, 1, 101),
                        "current or unrelated recipient lost a positive marker"))
                .thenExecute(() -> {
                    check(!received(kit, 0, 102), "private frame survived a new seat generation");
                    marker(kit, 0, 104);
                    server.matches().connection(kit.viewer(0).player().getUUID(), false);
                    server.matches().connection(kit.viewer(0).player().getUUID(), true);
                    marker(kit, 0, 105);
                })
                .thenWaitUntil(() -> check(received(kit, 0, 105), "reconnected recipient missed a current marker"))
                .thenExecute(() -> {
                    check(!received(kit, 0, 104), "private frame survived disconnect/reconnect");
                    marker(kit, 0, 106);
                    server.matches().join(rt, kit.viewer(0).player().getUUID(), PlayerSlot.ADMIN);
                    marker(kit, 0, 107);
                })
                .thenWaitUntil(() -> check(received(kit, 0, 107), "explicit observer missed a current marker"))
                .thenExecute(() -> {
                    check(!received(kit, 0, 106), "former primary frame survived an observer role change");
                    check(
                            !received(kit, 1, 102) && !received(kit, 1, 104) && !received(kit, 1, 106),
                            "issuer-only frame escaped to another participant");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void delayedFeedbackCannotRebindToTheIssuersNewRecipientContext(GameTestHelper h) {
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
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready()
                                && core(rt).queries().unitHandlesOf(new FactionSlot(0)).length == 1
                                && core(rt).queries().unitHandlesOf(new FactionSlot(1)).length == 1,
                        "units not ready"))
                .thenExecute(() -> kit.admit("reply", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenWaitUntil(() -> check(
                        !kit.payloads(0, MatchJoinPayload.class).isEmpty()
                                && !kit.payloads(1, MatchJoinPayload.class).isEmpty(),
                        "initial admission missing"))
                .thenExecute(() -> {
                    int first = core(rt).queries().unitHandlesOf(new FactionSlot(0))[0];
                    int second = core(rt).queries().unitHandlesOf(new FactionSlot(1))[0];
                    // Synchronous typed gateway entry ensures submission precedes the role change;
                    // the core result still runs asynchronously, after the roster has changed.
                    server.net()
                            .commands(
                                    kit.viewer(0).player(),
                                    CommandsPayload.of(new CommandBatch(
                                            600001, List.of(Move.replace(UnitSet.of(first), new Vec2d(24, 6))))));
                    server.matches().join(rt, kit.viewer(0).player().getUUID(), PlayerSlot.ADMIN);
                    server.net()
                            .commands(
                                    kit.viewer(1).player(),
                                    CommandsPayload.of(new CommandBatch(
                                            600002, List.of(Move.replace(UnitSet.of(second), new Vec2d(24, 18))))));
                })
                .thenWaitUntil(() -> check(
                        kit.payloads(1, FeedbackPayload.class).stream()
                                        .anyMatch(frame -> frame.message() instanceof Feedback reply
                                                && reply.clientSeq() == 600002
                                                && reply.outcome() == CommandOutcome.ACCEPTED)
                                && !core(rt).queries()
                                        .orders(core(rt).queries().unitHandlesOf(new FactionSlot(0))[0])
                                        .isEmpty(),
                        "positive peer reply or original command application missing"))
                .thenExecute(() -> {
                    for (int peer = 0; peer < 2; peer++)
                        check(
                                kit.payloads(peer, FeedbackPayload.class).stream()
                                        .noneMatch(frame -> frame.message() instanceof Feedback reply
                                                && reply.clientSeq() == 600001),
                                "delayed feedback rebound to a changed recipient or another peer");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    private static void marker(MultiplayerHarness kit, int player, int marker) {
        StrategosServer.get()
                .net()
                .send(
                        new Audience.Player(kit.viewer(player).player()),
                        new FeedbackPayload(new Feedback(1_000_000_000 + marker, CommandOutcome.ACCEPTED, null, 1, 1)));
    }

    private static boolean received(MultiplayerHarness kit, int player, int marker) {
        return kit.payloads(player, FeedbackPayload.class).stream()
                .anyMatch(frame ->
                        frame.message() instanceof Feedback feedback && feedback.clientSeq() == 1_000_000_000 + marker);
    }
}
