package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.MatchControl;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.CameraJumpPayload;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.DebugSubscribePayload;
import dev.strategos.mod.server.net.payload.MatchControlPayload;
import dev.strategos.mod.server.net.payload.ModeSwitchPayload;
import dev.strategos.mod.server.net.payload.TemplateAckPayload;
import dev.strategos.mod.server.net.payload.UiQueryPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The handlers of client-to-server payloads, on the server thread. A fault in one is logged and goes no further: it
 * never reaches the network layer, which would disconnect the client, nor the tick (a seated sender's command runs
 * inside its match's fault barrier, T15.2).
 */
public final class C2sHandlers {
    private static final Logger LOG = LoggerFactory.getLogger(C2sHandlers.class);

    private C2sHandlers() {}

    /** {@code handler}, its time counted as our work between ticks when it runs outside one (budget C). */
    public static <P extends CustomPacketPayload> IPayloadHandler<P> timed(IPayloadHandler<P> handler) {
        return (payload, context) -> {
            long start = System.nanoTime();
            try {
                handler.handle(payload, context);
            } finally {
                StrategosServer server = StrategosServer.getOrNull();
                if (server != null) {
                    server.betweenTicks().handled(System.nanoTime() - start);
                }
            }
        };
    }

    public static void commands(CommandsPayload payload, IPayloadContext context) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null || !(context.player() instanceof ServerPlayer player)) {
            return;
        }
        try {
            server.net().commands(player, payload);
        } catch (RuntimeException e) {
            LOG.error("the commands of {} failed", player.getGameProfile().getName(), e);
        }
    }

    /** A client's answer to a template set (DATA-03.6): a mismatch is resent. */
    public static void templateAck(TemplateAckPayload payload, IPayloadContext context) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null || !(context.player() instanceof ServerPlayer player)) {
            return;
        }
        try {
            server.templateSync().ack(server, player, payload.ack());
        } catch (RuntimeException e) {
            LOG.error("the template ack of {} failed", player.getGameProfile().getName(), e);
        }
    }

    /** A commander's camera jump (CAM-03): checked, queued for the next tick's start, or refused. */
    public static void cameraJump(CameraJumpPayload payload, IPayloadContext context) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null || !(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MatchRegistry.Seat seat = server.matches().seatOf(player.getUUID());
        if (seat != null) {
            server.commanders()
                    .jump(player, seat.match(), payload.jump(), server.server().getTickCount());
        }
    }

    /** A seated player's detail subscription (NET-08), kept by replication within its rate. */
    public static void uiQuery(UiQueryPayload payload, IPayloadContext context) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null || !(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MatchRegistry.Seat seat = server.matches().seatOf(player.getUUID());
        if (seat != null) {
            server.replication()
                    .query(player, seat, payload.query(), server.server().getTickCount());
        }
    }

    /**
     * A seated player's pause, resume or game speed (MATCH-05), in singleplayer only: a pause stops its match's steps
     * while commands keep coming in, a resume steps it again, and a speed offered sets the server's tick rate, which
     * in singleplayer is this match's alone. A match that is neither running nor paused, or a speed not offered, is
     * ignored.
     */
    public static void matchControl(MatchControlPayload payload, IPayloadContext context) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null
                || !(context.player() instanceof ServerPlayer player)
                || !server.server().isSingleplayer()) {
            return;
        }
        MatchRegistry.Seat seat = server.matches().seatOf(player.getUUID());
        MatchRuntime rt = seat == null ? null : seat.match();
        if (rt == null) {
            return;
        }
        MatchControl c = payload.control();
        switch (c.action()) {
            case MatchControl.PAUSE -> {
                if (rt.lifecycle() == MatchLifecycle.RUNNING) {
                    server.matches().pause(rt.id());
                }
            }
            case MatchControl.RESUME -> {
                if (rt.lifecycle() == MatchLifecycle.PAUSED) {
                    server.matches().resume(rt.id());
                }
            }
            default -> {
                if (MatchControl.offered(c.speed())) {
                    server.server().tickRateManager().setTickRate(20 * c.speed());
                }
            }
        }
    }

    /** A seated player's switch to first person or back (CAM-05), checked and applied by the commanders. */
    public static void modeSwitch(ModeSwitchPayload payload, IPayloadContext context) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null || !(context.player() instanceof ServerPlayer player)) {
            return;
        }
        MatchRegistry.Seat seat = server.matches().seatOf(player.getUUID());
        if (seat != null) {
            server.commanders()
                    .switchMode(
                            player,
                            seat.match(),
                            payload.request(),
                            server.server().getTickCount());
        }
    }

    /** An operator's overlay subscription (PATH-10), for the debug feed to check and keep. */
    public static void debugSubscribe(DebugSubscribePayload payload, IPayloadContext context) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null || !(context.player() instanceof ServerPlayer player)) {
            return;
        }
        try {
            server.debug().subscribe(player, payload.request());
        } catch (RuntimeException e) {
            LOG.error(
                    "the debug subscription of {} failed",
                    player.getGameProfile().getName(),
                    e);
        }
    }
}
