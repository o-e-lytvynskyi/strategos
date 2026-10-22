package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.QueueMode;
import dev.strategos.core.protocol.UnitSet;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.phys.Vec3;

/** Orders from players on a real server (ORD-02, spec §9.2 T18). */
public final class OrderTests {
    private OrderTests() {}

    /**
     * ORD-02.3: a seated mock player sends a queued move for its unit as a {@code commands} payload. Nothing changes in
     * the tick it arrives; at the next tick boundary the core validates it and the idle unit starts it, the journal
     * holds it under the player's slot and number, and the player hears it was accepted.
     */
    @StrategosTest(timeoutTicks = 200)
    public static void commandsPayloadAppliesNextTick(GameTestHelper h) {
        MatchRuntime rt = match(h);
        spawn(rt, 8.5, 8.5, 1, 0);
        Vec3 at = h.absoluteVec(new Vec3(16.5, 2, 16.5));
        MockViewer player = MockViewer.join(h.getLevel(), "strategos-orders", at.x, at.y, at.z, 2);
        StrategosServer.get().matches().join(rt, player.player().getUUID(), new PlayerSlot(0));
        var state = new Object() {
            long sentAt = -1;
            boolean applied;
        };
        h.onEachTick(() -> {
            player.poll();
            int[] units = handles(rt);
            if (units.length != 1) {
                return;
            }
            int unit = units[0];
            long tick = core(rt).queries().tick();
            if (state.sentAt < 0) {
                state.sentAt = tick;
                player.send(CommandsPayload.of(new CommandBatch(
                        5, List.of(new Move(UnitSet.of(unit), new Vec2d(24.5, 8.5), QueueMode.QUEUE, 0)))));
                check(core(rt).queries().orders(unit).isEmpty(), "the order applied in the tick it arrived");
                return;
            }
            if (!state.applied) {
                check(tick == state.sentAt + 1, "a tick passed without the order: " + tick);
                List<OrderQueues.Order> orders = core(rt).queries().orders(unit);
                check(
                        orders.size() == 1
                                && orders.get(0).x() == 24.5
                                && orders.get(0).z() == 8.5,
                        "the order was not applied at the next tick: " + orders);
                check(unit(rt, unit).state() == UnitState.WALKING, "the idle unit did not start its queued order");
                check(journaledAt(rt, tick), "the journal lacks the player's command at tick " + tick);
                state.applied = true;
                return; // its feedback goes out after the tick, with the gateway's flush
            }
            for (ClientboundCustomPayloadPacket p : player.capture().of(ClientboundCustomPayloadPacket.class)) {
                if (p.payload() instanceof FeedbackPayload f
                        && f.message() instanceof Feedback fb
                        && fb.clientSeq() == 5
                        && fb.outcome() == CommandOutcome.ACCEPTED) {
                    player.close();
                    h.succeed();
                    return;
                }
            }
        });
    }

    /** Whether the journal holds, at {@code tick}, a command of slot 0 numbered 5. */
    private static boolean journaledAt(MatchRuntime rt, long tick) {
        for (Journal.Segment segment : core(rt).journal().segments()) {
            for (TickInputs inputs : segment.nonEmptyTicks()) {
                if (inputs.tick() != tick) {
                    continue;
                }
                for (InputEvent event : inputs.events()) {
                    if (event instanceof CommandEnvelope e && e.issuer().value() == 0 && e.clientSeq() == 5) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
