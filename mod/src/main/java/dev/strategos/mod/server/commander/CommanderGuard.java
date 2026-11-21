package dev.strategos.mod.server.commander;

import dev.strategos.mod.StrategosServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** Server-owned vanilla-power decisions; client requests never supply membership or command mode. */
public final class CommanderGuard {
    private CommanderGuard() {}

    /** Entity-UUID spectator teleports are never an admission or camera-jump route for an active match member. */
    public static boolean deniesTeleport(ServerPlayer player) {
        StrategosServer holder = current(player);
        return holder != null && member(holder, player);
    }

    /** Keep the RTS commander's camera on its own body; ordinary spectators and first-person play retain vanilla. */
    public static boolean deniesAttackCamera(ServerPlayer player) {
        StrategosServer holder = current(player);
        return holder != null && member(holder, player) && holder.commanders().commands(player);
    }

    private static @Nullable StrategosServer current(ServerPlayer player) {
        StrategosServer holder = StrategosServer.getOrNull();
        if (holder == null
                || holder.server() != player.server
                || !holder.server().isSameThread()
                || !holder.net().acceptsLogin(player)) return null;
        return holder;
    }

    private static boolean member(StrategosServer holder, ServerPlayer player) {
        var seat = holder.matches().seatOf(player.getUUID());
        return seat != null && seat.match().roster().isConnected(player.getUUID());
    }
}
