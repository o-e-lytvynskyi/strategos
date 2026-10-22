package dev.strategos.mod.client.net;

import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.server.net.payload.DebugFeedPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.MatchStatePayload;
import dev.strategos.mod.server.net.payload.PayloadArrived;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-to-client payloads on the client (spec §2.7): the match this player joined and its state go to the client's
 * holder, and so does the debug feed; feedback is logged until the command card (M2) shows it. And the client's one
 * sender: rule B6 keeps {@code PacketDistributor} to the server's gateway, so the client sends on its connection.
 */
public final class ClientPayloads {
    private static final Logger LOG = LoggerFactory.getLogger(ClientPayloads.class);

    private ClientPayloads() {}

    public static void onPayload(PayloadArrived event) {
        switch (event.payload()) {
            case MatchJoinPayload p -> StrategosClient.match(p.join());
            case MatchStatePayload p -> StrategosClient.state(p.state());
            case FeedbackPayload p -> LOG.info("feedback: {}", p.message());
            case DebugFeedPayload p -> StrategosClient.debug().accept(p.message());
            default -> LOG.debug("unhandled payload {}", event.payload().type().id());
        }
    }

    /** Sends {@code payload} to the server if the connection has its channel; false when it has none. */
    public static boolean toServer(CustomPacketPayload payload) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(payload)) {
            return false;
        }
        connection.send(payload);
        return true;
    }
}
