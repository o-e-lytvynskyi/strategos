package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.SpawnUnits;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.gametest.net.MultiplayerHarness;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** T11.1: denying a stale reply is insufficient if its command still changes the replacement's match. */
public final class IngressIdentityTests {
    private IngressIdentityTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void aPredecessorConnectionCannotSubmitCommandsForItsReplacement(GameTestHelper h) {
        var rt = MatchFixture.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var server = StrategosServer.get();
        var faction = new FactionSlot(0);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, faction, new Vec2d(6, 6), 2, 2));
        MockViewer original = viewer(h);
        var state = new Object() {
            MockViewer active = original;
        };
        h.onEachTick(() -> state.active.poll());
        h.startSequence()
                .thenWaitUntil(() -> check(core(rt).queries().unitHandlesOf(faction).length == 2, "units not ready"))
                .thenExecute(() -> NetFixtures.seat(rt, original, 0))
                .thenWaitUntil(() -> check(
                        !NetFixtures.payloads(original, MatchJoinPayload.class).isEmpty(),
                        "original admission missing"))
                .thenExecute(() -> {
                    int[] handles = core(rt).queries().unitHandlesOf(faction);
                    check(
                            core(rt).queries().orders(handles[0]).isEmpty()
                                    && core(rt).queries().orders(handles[1]).isEmpty(),
                            "units already ordered");
                    original.close();
                    MockViewer replacement = viewer(h);
                    check(
                            original.player()
                                    .getUUID()
                                    .equals(replacement.player().getUUID()),
                            "replacement changed UUID");
                    state.active = replacement;
                    NetFixtures.seat(rt, replacement, 0);
                    // A queued handler can retain the old ServerPlayer even after UUID admission has changed.
                    server.net()
                            .commands(
                                    original.player(),
                                    CommandsPayload.of(new CommandBatch(
                                            910001, List.of(Move.replace(UnitSet.of(handles[0]), new Vec2d(40, 6))))));
                    server.net()
                            .commands(
                                    replacement.player(),
                                    CommandsPayload.of(new CommandBatch(
                                            910002, List.of(Move.replace(UnitSet.of(handles[1]), new Vec2d(40, 18))))));
                })
                .thenWaitUntil(() -> {
                    int fresh = core(rt).queries().unitHandlesOf(faction)[1];
                    check(
                            NetFixtures.payloads(state.active, FeedbackPayload.class).stream()
                                            .anyMatch(frame -> frame.message() instanceof Feedback reply
                                                    && reply.clientSeq() == 910002
                                                    && reply.outcome() == CommandOutcome.ACCEPTED)
                                    && !core(rt).queries().orders(fresh).isEmpty(),
                            "fresh connection's accepted command did not apply");
                })
                .thenExecute(() -> check(
                        core(rt).queries()
                                .orders(core(rt).queries().unitHandlesOf(faction)[0])
                                .isEmpty(),
                        "predecessor command mutated the replacement connection's match"))
                .thenExecute(() -> {
                    server.matches().dispose(rt.id());
                    state.active.close();
                    original.close();
                })
                .thenSucceed();
    }

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void repeatedOlderAndOverlappingBatchesCannotAppendOrders(GameTestHelper h) {
        var kit = MultiplayerHarness.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var rt = kit.match();
        var server = StrategosServer.get();
        var faction = new FactionSlot(0);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, faction, new Vec2d(6, 6), 1, 0));
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready() && core(rt).queries().unitHandlesOf(faction).length == 1,
                        "sequence fixture not ready"))
                .thenExecute(() -> kit.admit("sequence", List.of(new PlayerSlot(0))))
                .thenExecute(() -> {
                    int unit = core(rt).queries().unitHandlesOf(faction)[0];
                    var player = kit.viewer(0).player();
                    server.net().commands(player, queued(20, unit, 6, 12));
                    server.net().commands(player, queued(20, unit, 6, 12));
                    server.net().commands(player, queued(19, unit, 18));
                    server.net().commands(player, queued(21, unit, 24, 30));
                    server.net().commands(player, queued(22, unit, 36));
                })
                .thenWaitUntil(() -> check(
                        kit.payloads(0, FeedbackPayload.class).stream()
                                        .anyMatch(frame -> frame.message() instanceof Feedback reply
                                                && reply.clientSeq() == 22
                                                && reply.outcome() == CommandOutcome.ACCEPTED)
                                && core(rt).queries().orders(core(rt).queries().unitHandlesOf(faction)[0]).stream()
                                        .anyMatch(order -> order.x() == 40 && order.z() == 36),
                        "fresh sequence's accepted order missing"))
                .thenExecute(() -> check(
                        core(rt).queries().orders(core(rt).queries().unitHandlesOf(faction)[0]).stream()
                                .map(order -> order.z())
                                .toList()
                                .equals(List.of(6.0, 12.0, 36.0)),
                        "repeated/older/overlapping batch appended unauthorized orders"))
                .thenExecute(() -> {
                    int unit = core(rt).queries().unitHandlesOf(faction)[0];
                    server.net().joined(rt, kit.viewer(0).player(), new PlayerSlot(0));
                    server.net().commands(kit.viewer(0).player(), queued(0, unit, 42));
                })
                .thenWaitUntil(() -> check(
                        kit.payloads(0, FeedbackPayload.class).stream()
                                        .anyMatch(frame -> frame.message() instanceof Feedback reply
                                                && reply.clientSeq() == 0
                                                && reply.outcome() == CommandOutcome.ACCEPTED)
                                && core(rt).queries().orders(core(rt).queries().unitHandlesOf(faction)[0]).stream()
                                        .anyMatch(order -> order.z() == 42),
                        "new recipient view did not start its own sequence window"))
                .thenExecute(() -> check(
                        core(rt).queries().orders(core(rt).queries().unitHandlesOf(faction)[0]).stream()
                                .map(order -> order.z())
                                .toList()
                                .equals(List.of(6.0, 12.0, 36.0, 42.0)),
                        "view renewal discarded existing core orders"))
                .thenExecute(kit::close)
                .thenSucceed();
    }

    private static CommandsPayload queued(int sequence, int unit, double... targets) {
        java.util.ArrayList<Command> commands = new java.util.ArrayList<>();
        for (double z : targets) commands.add(new Move(UnitSet.of(unit), new Vec2d(40, z), QueueMode.QUEUE, 0));
        return CommandsPayload.of(new CommandBatch(sequence, commands));
    }

    private static MockViewer viewer(GameTestHelper h) {
        var at = h.absolutePos(new BlockPos(8, 20, 8));
        return MockViewer.join(h.getLevel(), "ingress-relogin", at.getX(), at.getY(), at.getZ(), 2);
    }
}
