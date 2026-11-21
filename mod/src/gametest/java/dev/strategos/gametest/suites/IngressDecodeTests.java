package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.check;
import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.SpawnUnits;
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
import dev.strategos.mod.server.net.payload.CameraJumpPayload;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.DebugSubscribePayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.LobbyActionPayload;
import dev.strategos.mod.server.net.payload.MatchControlPayload;
import dev.strategos.mod.server.net.payload.ModeSwitchPayload;
import dev.strategos.mod.server.net.payload.SkirmishPayload;
import dev.strategos.mod.server.net.payload.TemplateAckPayload;
import dev.strategos.mod.server.net.payload.UiQueryPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.connection.ConnectionType;

/** Actual decoders produce refusals that traverse registered handlers and the shared connection violation policy. */
public final class IngressDecodeTests {
    private IngressDecodeTests() {}

    @StrategosTest(arena = "arena_flat_48", timeoutTicks = 400)
    public static void mixedMalformedTypesPreserveValidTrafficUntilTheExactKickBoundary(GameTestHelper h) {
        var kit = MultiplayerHarness.create(h, MatchFixture.options(SimHarness.DEFAULT_SEED, h));
        var rt = kit.match();
        var server = StrategosServer.get();
        var counters = server.net().counters();
        var first = new FactionSlot(0);
        var second = new FactionSlot(1);
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, first, new Vec2d(6, 6), 1, 0));
        rt.submit(PlayerSlot.ADMIN, new SpawnUnits(TemplateSet.DEBUG_WALKER, second, new Vec2d(6, 18), 1, 0));
        var bad = List.of(
                empty(h, CommandsPayload.STREAM_CODEC),
                empty(h, LobbyActionPayload.STREAM_CODEC),
                empty(h, CameraJumpPayload.STREAM_CODEC),
                empty(h, ModeSwitchPayload.STREAM_CODEC),
                empty(h, MatchControlPayload.STREAM_CODEC),
                empty(h, SkirmishPayload.STREAM_CODEC),
                empty(h, TemplateAckPayload.STREAM_CODEC),
                empty(h, UiQueryPayload.STREAM_CODEC),
                empty(h, DebugSubscribePayload.STREAM_CODEC));
        String owner = "decoding-0";
        int before = counters.violations(owner);
        h.startSequence()
                .thenWaitUntil(() -> check(
                        kit.ready()
                                && core(rt).queries().unitHandlesOf(first).length == 1
                                && core(rt).queries().unitHandlesOf(second).length == 1,
                        "decoder fixture not ready"))
                .thenExecute(() -> kit.admit("decoding", List.of(new PlayerSlot(0), new PlayerSlot(1))))
                .thenExecute(() -> {
                    for (int i = 0; i < 100; i++) kit.viewer(0).send(bad.get(i % bad.size()));
                })
                .thenWaitUntil(() -> check(counters.violations(owner) - before == 100, "mixed refusals not counted"))
                .thenExecute(() -> {
                    check(!kit.viewer(0).disconnected(), "disconnected before exceeding 100 violations");
                    kit.sendCommands(
                            0,
                            new CommandBatch(
                                    77,
                                    List.of(Move.replace(
                                            UnitSet.of(core(rt).queries().unitHandlesOf(first)[0]),
                                            new Vec2d(40, 6)))));
                    kit.sendCommands(
                            1,
                            new CommandBatch(
                                    1,
                                    List.of(Move.replace(
                                            UnitSet.of(core(rt).queries().unitHandlesOf(second)[0]),
                                            new Vec2d(40, 18)))));
                })
                .thenWaitUntil(() -> check(
                        accepted(kit, 0, 77)
                                && accepted(kit, 1, 1)
                                && !core(rt).queries()
                                        .orders(core(rt).queries().unitHandlesOf(first)[0])
                                        .isEmpty()
                                && !core(rt).queries()
                                        .orders(core(rt).queries().unitHandlesOf(second)[0])
                                        .isEmpty(),
                        "legitimate command or unaffected peer lost its positive application"))
                .thenExecute(() -> {
                    check(rt.lifecycle() == MatchLifecycle.RUNNING, "malformed control changed match lifecycle");
                    var seat = server.matches().seatOf(kit.viewer(0).player().getUUID());
                    check(
                            seat != null && seat.match() == rt && seat.slot().equals(new PlayerSlot(0)),
                            "malformed input changed membership");
                    var reasons = counters.toJson().getAsJsonObject("violations");
                    for (String reason : List.of(
                            "unreadable",
                            "invalid_lobby_action",
                            "invalid_camera_jump",
                            "invalid_mode_switch",
                            "invalid_match_control",
                            "invalid_skirmish",
                            "invalid_template_ack",
                            "invalid_ui_query",
                            "invalid_debug_subscribe")) {
                        check(reasons.has(owner + " " + reason), "missing stable reason " + reason);
                    }
                    kit.viewer(0).send(bad.get(0));
                })
                .thenWaitUntil(() -> check(kit.viewer(0).disconnected(), "101st violation did not disconnect"))
                .thenExecute(() -> {
                    check(counters.violations(owner) - before == 101, "kick boundary counted incorrectly");
                    check(!kit.viewer(1).disconnected(), "offender's policy disconnected the unrelated peer");
                    check(rt.lifecycle() == MatchLifecycle.RUNNING, "malformed traffic faulted the match");
                })
                .thenExecute(kit::close)
                .thenSucceed();
    }

    private static boolean accepted(MultiplayerHarness kit, int player, int sequence) {
        return kit.payloads(player, FeedbackPayload.class).stream()
                .anyMatch(frame -> frame.message() instanceof Feedback reply
                        && reply.clientSeq() == sequence
                        && reply.outcome() == CommandOutcome.ACCEPTED);
    }

    private static <P extends CustomPacketPayload> P empty(
            GameTestHelper h, StreamCodec<RegistryFriendlyByteBuf, P> codec) {
        var buf = new RegistryFriendlyByteBuf(
                Unpooled.wrappedBuffer(new byte[0]), h.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            return codec.decode(buf);
        } finally {
            buf.release();
        }
    }
}
