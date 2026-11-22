package dev.strategos.mod.server.commander;

import dev.strategos.core.sim.vision.VisionGrid;
import dev.strategos.mod.StrategosServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
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

    /** Backstop for accepted vanilla movement and external dimension changes; never applies to first-person play. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        StrategosServer holder = current(player);
        if (holder == null || !member(holder, player) || !holder.commanders().commands(player)) return;
        var seat = holder.matches().seatOf(player.getUUID());
        if (seat == null) return;
        var space = seat.match().space();
        var level = holder.server().getLevel(space.level());
        if (level == null) return;
        int reach = CommanderService.REACH;
        double x = bounded(
                player.getX(), space.origin().getX() - reach, space.origin().getX() + space.sizeX() + reach);
        double y = bounded(player.getY(), space.minY() - reach, space.maxY() + 2 * reach);
        double z = bounded(
                player.getZ(), space.origin().getZ() - reach, space.origin().getZ() + space.sizeZ() + reach);
        if (!player.level().dimension().equals(space.level())
                || player.getX() != x
                || player.getY() != y
                || player.getZ() != z) {
            player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
            // The listener restores firstGood* after doTick returns; publish the corrected origin as well.
            player.connection.resetPosition();
        }
    }

    static double bounded(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min + (max - min) / 2;
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
