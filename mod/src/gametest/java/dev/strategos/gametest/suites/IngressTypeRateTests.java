package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.DebugLayer;
import dev.strategos.core.protocol.DebugSubscribe;
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
import dev.strategos.gametest.net.IngressContext;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.net.C2sHandlers;
import dev.strategos.mod.server.net.payload.DebugFeedPayload;
import dev.strategos.mod.server.net.payload.DebugSubscribePayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.UiQueryPayload;
import dev.strategos.mod.server.net.payload.UnitDetailPayload;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;

/** Literal per-type limits plus actual accepted subscription output and unaffected simulation commands. */
public final class IngressTypeRateTests {
    private IngressTypeRateTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void queryAndDebugCapsRetainSubscriptionsAndRefillWithoutStarvingCommands(GameTestHelper h) {
        var kit = MultiplayerHarness.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var rt = kit.match();
        var server = StrategosServer.get();
        for (int i = 0; i < 2; i++) {
            rt.submit(
                    PlayerSlot.ADMIN,
                    new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(i), new Vec2d(6, 6 + 12 * i), 1, 0));
        }
        var state = new Object() {
            int violations;
            int debug;
            int query;
            int details;
            int feeds;
        };
        h.startSequence()
                .thenWaitUntil(() ->
                        check(kit.ready() && core(rt).queries().unitHandles().length == 2, "rate fixture not ready"))
                .thenExecute(() -> kit.admit("typecap", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenExecute(() -> {
                    kit.viewer(0).operator(2);
                    state.violations = server.net().counters().violations("typecap-0");
                    state.debug = rejected("debug_rate");
                    state.query = rejected("ui_query_rate");
                    burst(kit);
                    move(kit, 0, 1);
                    move(kit, 1, 1);
                })
                .thenWaitUntil(() -> check(positive(kit, 1), "accepted subscription/command controls missing"))
                .thenExecute(() -> {
                    check(
                            rejected("debug_rate") == state.debug + 1,
                            "third debug request was not the only refused subscription");
                    check(rejected("ui_query_rate") == state.query + 1, "fifth query was not the only refused query");
                })
                .thenExecuteAfter(20, () -> {
                    state.details = kit.payloads(0, UnitDetailPayload.class).size();
                    state.feeds = kit.payloads(0, DebugFeedPayload.class).size();
                    burst(kit);
                    move(kit, 0, 2);
                    move(kit, 1, 2);
                })
                .thenWaitUntil(() -> check(
                        positive(kit, 2)
                                && kit.payloads(0, UnitDetailPayload.class).size() > state.details
                                && kit.payloads(0, DebugFeedPayload.class).size() > state.feeds,
                        "refilled subscription or continued commands lost their positive output"))
                .thenExecute(() -> {
                    check(
                            rejected("debug_rate") == state.debug + 2,
                            "debug window did not refill exactly two subscriptions");
                    check(
                            rejected("ui_query_rate") == state.query + 2,
                            "query window did not refill exactly four queries");
                    check(
                            server.net().counters().violations("typecap-0") == state.violations + 2,
                            "semantic query cooldown changed the protocol violation budget");
                    check(!kit.viewer(0).disconnected() && !kit.viewer(1).disconnected(), "valid peer disconnected");
                    check(rt.lifecycle() == MatchLifecycle.RUNNING, "subscription traffic changed lifecycle");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    private static void burst(MultiplayerHarness kit) {
        var context = IngressContext.capture(kit.viewer(0).player());
        int own = core(kit.match()).queries().unitHandlesOf(new FactionSlot(0))[0];
        var query = C2sHandlers.<UiQueryPayload>timed(C2sHandlers::uiQuery);
        for (int i = 0; i < 4; i++) query.handle(new UiQueryPayload(new UiQuery(List.of(own))), context);
        query.handle(new UiQueryPayload(new UiQuery(List.of())), context); // must not clear the accepted subscription
        var debug = C2sHandlers.<DebugSubscribePayload>timed(C2sHandlers::debugSubscribe);
        var units =
                new DebugSubscribePayload(new DebugSubscribe(DebugLayer.UNITS.bit(), DebugSubscribe.DEFAULT_RADIUS));
        debug.handle(units, context);
        debug.handle(units, context);
        debug.handle(new DebugSubscribePayload(DebugSubscribe.NONE), context); // must not unsubscribe
    }

    private static boolean positive(MultiplayerHarness kit, int sequence) {
        int own = core(kit.match()).queries().unitHandlesOf(new FactionSlot(0))[0];
        return kit.payloads(0, UnitDetailPayload.class).stream()
                        .anyMatch(p -> p.detail().units().stream().anyMatch(u -> u.handle() == own))
                && kit.payloads(0, DebugFeedPayload.class).stream()
                        .anyMatch(p -> p.message().layer() == DebugLayer.UNITS)
                && accepted(kit, 0, sequence)
                && accepted(kit, 1, sequence)
                && !core(kit.match()).queries().orders(own).isEmpty();
    }

    private static int rejected(String reason) {
        var audit = StrategosServer.get().net().counters().toJson().getAsJsonObject("rejections");
        String key = "typecap-0 " + reason;
        return audit.has(key) ? audit.get(key).getAsInt() : 0;
    }

    private static void move(MultiplayerHarness kit, int player, int sequence) {
        int handle = core(kit.match()).queries().unitHandlesOf(new FactionSlot(player))[0];
        kit.sendCommands(
                player,
                new CommandBatch(sequence, List.of(Move.replace(UnitSet.of(handle), new Vec2d(40, 6 + 12 * player)))));
    }

    private static boolean accepted(MultiplayerHarness kit, int player, int sequence) {
        return kit.payloads(player, FeedbackPayload.class).stream()
                .anyMatch(p -> p.message() instanceof Feedback f
                        && f.clientSeq() == sequence
                        && f.outcome() == CommandOutcome.ACCEPTED);
    }
}
