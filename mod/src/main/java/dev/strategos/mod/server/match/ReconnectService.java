package dev.strategos.mod.server.match;

import dev.strategos.mod.server.commander.CommanderService;
import dev.strategos.mod.server.net.IngressReason;
import dev.strategos.mod.server.net.NetGateway;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Reserved UUID admission; the core's existing orders and faction/grants are never recreated by a client. */
public final class ReconnectService {
    private final MinecraftServer server;
    private final MatchRegistry matches;
    private final CommanderService commanders;
    private final NetGateway net;

    public ReconnectService(
            MinecraftServer server, MatchRegistry matches, CommanderService commanders, NetGateway net) {
        this.server = server;
        this.matches = matches;
        this.commanders = commanders;
        this.net = net;
    }

    public void loggedIn(ServerPlayer player) {
        if (!net.acceptsLogin(player)) return;
        MatchRegistry.Seat seat = matches.seatOf(player.getUUID());
        if (seat == null) {
            commanders.leave(server, player);
            return;
        }
        if (!matches.connection(player.getUUID(), true)) {
            net.rejected(player, IngressReason.RECONNECT_EXPIRED);
            commanders.leave(server, player);
            player.sendSystemMessage(Component.translatable("message.strategos.reconnect_expired"));
            return;
        }
        commanders.enter(player, seat.match());
        net.joined(seat.match(), player, seat.slot());
    }
}
