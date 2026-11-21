package dev.strategos.mod.server.commander;

import dev.strategos.core.sim.vision.VisionGrid;
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

    /** Additional player-body privacy gate; the caller must retain every vanilla eligibility check. */
    public static boolean allowsBody(ServerPlayer subject, ServerPlayer viewer) {
        StrategosServer holder = StrategosServer.getOrNull();
        if (holder == null) return true;
        if (!holder.server().isSameThread()) return false;
        var target = holder.matches().seatOf(subject.getUUID());
        if (target == null) return true;
        var seat = holder.matches().seatOf(viewer.getUUID());
        if (seat == null || !seat.match().id().equals(target.match().id())) return false;
        if (!holder.net().acceptsLogin(viewer)
                || !holder.net().acceptsLogin(subject)
                || !seat.match().roster().isConnected(viewer.getUUID())
                || !target.match().roster().isConnected(subject.getUUID())) return false;
        if (!seat.hasPerspective() || !target.hasPerspective()) return false;
        if (seat.slot().isAdmin()) return true;
        var primary = seat.primaryFaction();
        var targetPrimary = target.primaryFaction();
        if (primary.isEmpty() || targetPrimary.isEmpty()) return false;
        if (primary.orElseThrow().equals(targetPrimary.orElseThrow())) return true;
        // Allied factions do not share vision in M7. Grants never change this primary perspective.
        if (subject.isSpectator()) return false;
        var rt = seat.match();
        var core = rt.core();
        if (core == null || !subject.level().dimension().equals(rt.space().level())) return false;
        if (core.queries().config().rules().play().revealMap()) return true;
        var vision = core.vision();
        if (vision == null || !Double.isFinite(subject.getX()) || !Double.isFinite(subject.getZ())) return false;
        return vision.grid(primary.orElseThrow().value())
                        .count(
                                VisionGrid.cellOf(
                                        subject.getX() - rt.space().origin().getX()),
                                VisionGrid.cellOf(
                                        subject.getZ() - rt.space().origin().getZ()))
                > 0;
    }

    // The gateway pins the exact player/listener/open transport to this server, not just a matching UUID.
    private static @Nullable StrategosServer current(ServerPlayer player) {
        StrategosServer holder = StrategosServer.getOrNull();
        if (holder == null || !holder.server().isSameThread() || !holder.net().acceptsLogin(player)) return null;
        return holder;
    }

    private static boolean member(StrategosServer holder, ServerPlayer player) {
        var seat = holder.matches().seatOf(player.getUUID());
        return seat != null && seat.match().roster().isConnected(player.getUUID());
    }
}
