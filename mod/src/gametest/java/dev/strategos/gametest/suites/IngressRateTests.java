package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.TemplateAck;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.net.C2sHandlers;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.TemplateAckPayload;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Valid auxiliary traffic is bounded independently of unit commands and the existing per-type backend cooldown. */
public final class IngressRateTests {
    private IngressRateTests() {}

    @StrategosTest(arena = "arena_flat_48", batch = "ingress_aux_rate", solo = true, timeoutTicks = 400)
    public static void auxiliaryBurstsCannotStarveUnitCommandsAndHaveAnExactCappedRefill(GameTestHelper h) {
        var kit = MultiplayerHarness.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var rt = kit.match();
        var server = StrategosServer.get();
        var counters = server.net().counters();
        var first = new FactionSlot(0);
        var second = new FactionSlot(1);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, first, new Vec2d(6, 6), 1, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, second, new Vec2d(6, 18), 1, 0));
        var ack = new TemplateAckPayload(new TemplateAck(new byte[TemplateAck.SHA_BYTES]));
        var state = new Object() {
            long resends;
            int violations;
        };
        String owner = "auxrate-0";
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready()
                                && core(rt).queries().unitHandlesOf(first).length == 1
                                && core(rt).queries().unitHandlesOf(second).length == 1,
                        "auxiliary fixture not ready"))
                .thenExecute(() -> kit.admit("auxrate", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenExecute(() -> {
                    state.resends = server.templateSync().resent();
                    state.violations = counters.violations(owner);
                    burst(kit, ack);
                    move(kit, 0, 1, 6);
                    move(kit, 1, 1, 18);
                })
                .thenWaitUntil(() -> check(
                        server.templateSync().resent() > state.resends
                                && accepted(kit, 0, 1)
                                && accepted(kit, 1, 1)
                                && !core(rt).queries()
                                        .orders(core(rt).queries().unitHandlesOf(first)[0])
                                        .isEmpty()
                                && !core(rt).queries()
                                        .orders(core(rt).queries().unitHandlesOf(second)[0])
                                        .isEmpty(),
                        "actual auxiliary backend or legitimate own/peer command positive missing"))
                .thenExecute(() -> check(
                        counters.violations(owner) - state.violations == 1,
                        "81st auxiliary request was not the single refused request"))
                .thenExecuteAfter(40, () -> {
                    burst(kit, ack);
                    move(kit, 0, 2, 12);
                    move(kit, 1, 2, 24);
                })
                .thenWaitUntil(() -> check(
                        server.templateSync().resent() >= state.resends + 2
                                && accepted(kit, 0, 2)
                                && accepted(kit, 1, 2),
                        "refilled auxiliary backend or continued legitimate commands missing"))
                .thenExecute(() -> {
                    check(
                            counters.violations(owner) - state.violations == 2,
                            "refill exceeded the capped 80-request burst");
                    var reasons = counters.toJson().getAsJsonObject("violations");
                    check(
                            reasons.has(owner + " auxiliary_rate")
                                    && reasons.get(owner + " auxiliary_rate").getAsInt() == 2,
                            "auxiliary admission did not record its stable reason");
                    check(
                            !kit.viewer(0).disconnected() && !kit.viewer(1).disconnected(),
                            "two refusals disconnected a valid peer");
                    check(rt.lifecycle() == MatchLifecycle.RUNNING, "auxiliary traffic faulted or paused the match");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    private static void burst(MultiplayerHarness kit, TemplateAckPayload ack) {
        var player = kit.viewer(0).player();
        var listener = player.connection;
        var transport = listener.getConnection();
        IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(
                IPayloadContext.class.getClassLoader(),
                new Class<?>[] {IPayloadContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("player")) return player;
                    if (method.getName().equals("listener")) return listener;
                    if (method.getName().equals("connection")) return transport;
                    throw new UnsupportedOperationException("unused test context method " + method.getName());
                });
        var handler = C2sHandlers.<TemplateAckPayload>timed(C2sHandlers::templateAck);
        // Invoke the actual registered decorator/handler synchronously so one burst has one server-tick budget.
        for (int i = 0; i < 81; i++) handler.handle(ack, context);
    }

    private static void move(MultiplayerHarness kit, int player, int sequence, int z) {
        int handle = core(kit.match()).queries().unitHandlesOf(new FactionSlot(player))[0];
        kit.sendCommands(
                player, new CommandBatch(sequence, List.of(Move.replace(UnitSet.of(handle), new Vec2d(40, z)))));
    }

    private static boolean accepted(MultiplayerHarness kit, int player, int sequence) {
        return kit.payloads(player, FeedbackPayload.class).stream()
                .anyMatch(frame -> frame.message() instanceof Feedback reply
                        && reply.clientSeq() == sequence
                        && reply.outcome() == CommandOutcome.ACCEPTED);
    }
}
