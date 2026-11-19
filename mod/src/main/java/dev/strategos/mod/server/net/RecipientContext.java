package dev.strategos.mod.server.net;

import dev.strategos.core.shared.FactionSlot;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRegistry.Seat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;

/** Server-thread origin of an outgoing frame; UUID equality alone cannot authorize a later connection. */
record RecipientContext(
        ServerPlayer player,
        ServerGamePacketListenerImpl connection,
        @Nullable Seat seat,
        long generation,
        int primary) {
    static RecipientContext capture(ServerPlayer player, MatchRegistry registry) {
        Seat seat = registry.seatOf(player.getUUID());
        return new RecipientContext(
                player,
                player.connection,
                seat,
                seat == null ? 0 : seat.match().roster().generationOf(player.getUUID()),
                seat == null ? -1 : primary(seat));
    }

    boolean current(ServerPlayer online, MatchRegistry registry) {
        if (online != player || online.connection != connection) return false;
        Seat now = registry.seatOf(online.getUUID());
        Seat before = seat;
        if (before == null) return now == null;
        return now != null
                && now.match() == before.match()
                && now.slot().equals(before.slot())
                && generation != 0
                && now.match().roster().generationOf(online.getUUID()) == generation
                && primary(now) == primary;
    }

    private static int primary(Seat seat) {
        return seat.primaryFaction().map(FactionSlot::value).orElse(-1);
    }
}
