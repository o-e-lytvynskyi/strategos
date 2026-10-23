package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;
import static dev.strategos.gametest.suites.Units.handles;
import static dev.strategos.gametest.suites.Units.match;
import static dev.strategos.gametest.suites.Units.spawn;
import static dev.strategos.gametest.suites.Units.unit;

import dev.strategos.core.content.TemplateSet;
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
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.TickInputs;
import dev.strategos.core.sim.UnitState;
import dev.strategos.core.sim.UnitView;
import dev.strategos.core.sim.journal.Journal;
import dev.strategos.core.sim.order.OrderQueues;
import dev.strategos.core.testkit.SimHarness;
import dev.strategos.gametest.MatchFixture;
import dev.strategos.gametest.StrategosTest;
import dev.strategos.gametest.net.MockViewer;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.level.block.Blocks;
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

    /**
     * ORD-03.2: on a 64 x 48 floor laid by the test, fifty walkers spawned in a disc of radius 6 round (10, 24) get one
     * Move to (46.5, 24.5): each comes to rest within 1.5 blocks of its place, the one it holds then (members trade
     * places in a crowd), and 60 ticks after all rest no two stand nearer than 0.6; a single walker sent alone to (46.5,
     * 8.5) comes to rest within 0.5 of it.
     */
    @StrategosTest(arena = "arena_air_96", timeoutTicks = 1_200)
    public static void fiftySettleInSlots(GameTestHelper h) {
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 48; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.SMOOTH_STONE); // relative y 1 is the floor layer
            }
        }
        MatchRuntime rt = MatchFixture.create(h, 0, 0, 64, 48, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(0), new Vec2d(10, 24), 50, 6));
        rt.submit(
                PlayerSlot.ADMIN,
                new SpawnUnits(TemplateSet.DEBUG_WALKER, new FactionSlot(1), new Vec2d(10.5, 8.5), 1, 0));
        var state = new Object() {
            int[] group = new int[0];
            int alone;
            double[] placeX = new double[0];
            double[] placeZ = new double[0];
            double[] settled = new double[0];
            double aloneSettled = -1;
            long restedAt = -1;
        };
        h.onEachTick(() -> {
            MatchCore core = core(rt);
            int[] group = core.queries().unitHandlesOf(new FactionSlot(0));
            int[] alone = core.queries().unitHandlesOf(new FactionSlot(1));
            if (state.group.length == 0) {
                if (group.length < 50 || alone.length < 1) {
                    return;
                }
                state.group = group;
                state.alone = alone[0];
                rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(group), new Vec2d(46.5, 24.5)));
                rt.submit(PlayerSlot.ADMIN, Move.replace(UnitSet.of(alone[0]), new Vec2d(46.5, 8.5)));
                return;
            }
            if (state.placeX.length == 0) {
                state.placeX = new double[50];
                state.placeZ = new double[50];
                state.settled = new double[50];
                for (int k = 0; k < 50; k++) {
                    List<OrderQueues.Order> orders = core.queries().orders(state.group[k]);
                    check(!orders.isEmpty() && orders.get(0).slot() > 0, "a member without a place: " + orders);
                    state.placeX[k] = orders.get(0).x();
                    state.placeZ[k] = orders.get(0).z();
                    state.settled[k] = -1;
                }
                return;
            }
            for (int k = 0; k < 50; k++) { // members trade places in a crowd (spec §1.6 D row 151)
                List<OrderQueues.Order> orders = core.queries().orders(state.group[k]);
                if (orders.isEmpty()
                        || (orders.get(0).x() == state.placeX[k]
                                && orders.get(0).z() == state.placeZ[k])) {
                    continue;
                }
                for (int m = 0; m < 50; m++) { // the one that took this member's place got its new one
                    if (m != k
                            && state.placeX[m] == orders.get(0).x()
                            && state.placeZ[m] == orders.get(0).z()) {
                        state.placeX[m] = state.placeX[k];
                        state.placeZ[m] = state.placeZ[k];
                    }
                }
                state.placeX[k] = orders.get(0).x();
                state.placeZ[k] = orders.get(0).z();
            }
            boolean all = true;
            for (int k = 0; k < 50; k++) {
                UnitView u = unit(rt, state.group[k]);
                if (u.state() == UnitState.IDLE) {
                    if (state.settled[k] < 0) {
                        state.settled[k] = Math.hypot(u.x() - state.placeX[k], u.z() - state.placeZ[k]);
                        check(
                                state.settled[k] <= 1.5 + 1e-9,
                                "a member came to rest " + state.settled[k] + " from its place");
                    }
                } else {
                    all = false;
                }
            }
            UnitView lone = unit(rt, state.alone);
            if (lone.state() == UnitState.IDLE && state.aloneSettled < 0) {
                state.aloneSettled = Math.hypot(lone.x() - 46.5, lone.z() - 8.5);
                check(state.aloneSettled <= 0.5, "the lone walker came to rest " + state.aloneSettled + " off");
            }
            if (!all || state.aloneSettled < 0) {
                state.restedAt = -1;
                return;
            }
            long tick = core.queries().tick();
            if (state.restedAt < 0) {
                state.restedAt = tick;
            }
            if (tick - state.restedAt < 60) {
                return; // standing members part slowly, to 0.6 in the limit
            }
            for (int a = 0; a < 50; a++) {
                UnitView ua = unit(rt, state.group[a]);
                for (int b = 0; b < a; b++) {
                    UnitView ub = unit(rt, state.group[b]);
                    double gap = Math.hypot(ua.x() - ub.x(), ua.z() - ub.z());
                    check(gap >= 0.6 - 1e-3, "two members stand " + gap + " apart at rest");
                }
            }
            h.succeed();
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
