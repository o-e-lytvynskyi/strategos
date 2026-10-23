package dev.strategos.mod.server.content;

import dev.strategos.core.protocol.TemplateAck;
import dev.strategos.core.protocol.TemplateSetView;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.server.net.payload.TemplateSetPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * The template set to every client (DATA-03, spec §6.6): on {@code OnDatapackSyncEvent}, posted at login before the
 * player enters the level and after every {@code /reload} (PlayerList.java:208, 920), the set goes to the player or to
 * every relevant one at once, ahead of any body it could pair. Each client answers with the set's SHA-256; a mismatch
 * is resent, at most once a second per player. Server thread only.
 */
public final class TemplateSync {
    /** Ticks between two resends to one player. */
    static final int RESEND_EVERY = 20;

    private final Map<UUID, Long> lastResend = new HashMap<>();
    private long sent;
    private long resent;

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null) {
            return;
        }
        event.getRelevantPlayers().forEach(player -> server.templateSync().send(server, player));
    }

    /** Sends the server's set to {@code player} now, ahead of this tick's other payloads. */
    public void send(StrategosServer server, ServerPlayer player) {
        ServerTemplates templates = server.templates();
        TemplateSetView view = TemplateSetView.of(templates.set(), templates.table());
        server.net().sendNow(player, new TemplateSetPayload(view));
        sent++;
    }

    /** A client's answer: a set other than the server's is resent, at most every {@value #RESEND_EVERY} ticks. */
    public void ack(StrategosServer server, ServerPlayer player, TemplateAck ack) {
        if (ack.matches(server.templates().set().version())) {
            return;
        }
        long now = server.server().getTickCount();
        Long last = lastResend.get(player.getUUID());
        if (last != null && now - last < RESEND_EVERY) {
            return;
        }
        lastResend.put(player.getUUID(), now);
        resent++;
        send(server, player);
    }

    public void forget(ServerPlayer player) {
        lastResend.remove(player.getUUID());
    }

    /** Sets sent, resends included. */
    public long sent() {
        return sent;
    }

    /** Resends after a mismatched answer. */
    public long resent() {
        return resent;
    }
}
