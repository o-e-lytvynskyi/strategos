package dev.strategos.mod.client.menu;

import dev.strategos.mod.client.net.ClientPayloads;
import dev.strategos.mod.server.net.LobbyAction;
import dev.strategos.mod.server.net.LobbyReply;
import dev.strategos.mod.server.net.LobbyState;
import dev.strategos.mod.server.net.payload.LobbyActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/** Client-thread bridge between the private protocol and the screen's canonical session. */
public final class ClientLobby {
    private static final LobbyClientSession SESSION = new LobbyClientSession();
    private static final LobbyActionPayload PROBE = LobbyActionPayload.of(new LobbyAction.JoinAvailable());

    private ClientLobby() {}

    public static LobbyClientSession session() {
        return SESSION;
    }

    public static boolean available() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(PROBE);
    }

    public static boolean mayHost() {
        var mc = Minecraft.getInstance();
        return mc.hasSingleplayerServer() || (mc.player != null && mc.player.hasPermissions(2));
    }

    public static void open(@Nullable Screen parent) {
        Minecraft.getInstance().setScreen(new MultiplayerLobbyScreen(parent));
    }

    public static void request(LobbyAction action) {
        if (SESSION.waiting()) return;
        SESSION.beginRequest();
        if (!ClientPayloads.toServer(LobbyActionPayload.of(action))) SESSION.refuse(LobbyReply.INVALID);
    }

    public static void accept(LobbyState state) {
        if (!SESSION.accept(state)) return;
        var mc = Minecraft.getInstance();
        if (state.phase() == LobbyState.Phase.STARTED && mc.screen instanceof MultiplayerLobbyScreen)
            mc.setScreen(null);
    }

    public static void reply(LobbyReply reply) {
        SESSION.refuse(reply);
    }

    public static void clear() {
        SESSION.clear();
    }

    public static void leave() {
        var model = SESSION.model();
        boolean cancel = SESSION.waiting() || (model != null && model.mayLeave());
        SESSION.clear();
        if (cancel) ClientPayloads.toServer(LobbyActionPayload.of(new LobbyAction.Cancel()));
    }
}
