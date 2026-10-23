package dev.strategos.mod.client.net;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.TemplateAck;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.server.net.payload.DebugFeedPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.MatchStatePayload;
import dev.strategos.mod.server.net.payload.PayloadArrived;
import dev.strategos.mod.server.net.payload.TemplateAckPayload;
import dev.strategos.mod.server.net.payload.TemplateSetPayload;
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
            case TemplateSetPayload p -> templates(p);
            default -> LOG.debug("unhandled payload {}", event.payload().type().id());
        }
    }

    /**
     * The server's template set (DATA-03.6): decoded and kept, then acknowledged with the SHA-256 of what this client
     * decoded; a set that does not decode is acknowledged with zeros, so the server sends it again.
     */
    static void templates(TemplateSetPayload payload) {
        byte[] sha = new byte[TemplateAck.SHA_BYTES];
        try {
            TemplateSet set = payload.view().toSet();
            StrategosClient.templates(set);
            sha = set.sha();
        } catch (IllegalArgumentException e) {
            LOG.error("the server's template set does not decode", e);
        }
        toServer(new TemplateAckPayload(new TemplateAck(sha)));
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
