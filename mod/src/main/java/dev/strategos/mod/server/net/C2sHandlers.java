package dev.strategos.mod.server.net;

import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.DebugSubscribePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
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
