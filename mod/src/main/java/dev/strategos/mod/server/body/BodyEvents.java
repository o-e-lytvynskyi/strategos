package dev.strategos.mod.server.body;

import dev.strategos.core.diag.MatchLog;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.vision.VisibilityIndex;
import dev.strategos.core.sim.vision.VisionSystem;
import dev.strategos.mod.StrategosServer;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import dev.strategos.mod.server.fog.FogGate;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The world's events about unit bodies, routed to their match, server side only. */
public final class BodyEvents {
    private static final Logger LOG = LoggerFactory.getLogger(BodyEvents.class);

    private BodyEvents() {}

    /** Only a body registry creates bodies: an unbound unit body is refused and logged. */
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof UnitEntity body) || body.isBound()) {
            return;
        }
        event.setCanceled(true);
        LOG.warn(
                MatchLog.NO_MATCH + "Refused an unbound unit body at {}: only a match's body registry creates bodies",
                body.position());
    }

    /**
     * A body left its level: its match's registry decides, inside the match's fault barrier. Bodies of no match (the
     * spikes' use id 0) and everything after the server stopped running (its shutdown unloads every body) are ignored.
     */
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof UnitEntity body)) {
            return;
        }
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null || !server.server().isRunning()) {
            return;
        }
        MatchRuntime rt = runtimeOf(server, body);
        if (rt == null) {
            return;
        }
        server.faults().run(rt, "body left", () -> {
            if (rt.bodies().onLeft(body)) {
                UnitSpawnData data = body.spawnData();
                rt.log()
                        .warn(
                                rt.tick(),
                                "the body of unit {} left the level ({}); it comes back next tick",
                                data == null ? "?" : Handle.toString(data.handle()),
                                String.valueOf(body.getRemovalReason()));
            }
        });
    }

    /** From {@code UnitEntity#hurt}, server side: M0 ignores damage; CMB-01 (M2) turns it into core damage. */
    public static void onHurt(UnitEntity body, DamageSource source, float amount) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null) {
            return;
        }
        MatchRuntime rt = runtimeOf(server, body);
        if (rt == null) {
            return;
        }
        UnitSpawnData data = body.spawnData();
        if (data == null) {
            return;
        }
        server.faults().run(rt, "hurt", () -> rt.damage(DamageBridge.of(data.handle(), source, amount)));
    }

    /**
     * Whether vanilla's tracking may pair {@code player} with {@code body}'s unit (the fog gate, FOW-07 / MATCH-07):
     * match membership, then per-unit visibility from the viewer's {@code VisibilityIndex}; a revealed map shows every
     * body to every participant.
     */
    public static boolean canSee(UnitSpawnData body, ServerPlayer player) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null) {
            return FogGate.decide(body.matchId(), 0);
        }
        MatchRegistry.Seat seat = server.matches().seatOf(player.getUUID());
        if (seat == null) {
            return FogGate.decide(body.matchId(), 0);
        }
        int viewerMatch = seat.match().id().value();
        if (viewerMatch != body.matchId()) {
            return false;
        }
        MatchRuntime rt = seat.match();
        MatchCore core = rt.core();
        if (core == null) {
            return false;
        }
        if (core.queries().config().rules().play().revealMap()) {
            return true;
        }
        int viewerFaction = seat.slot().isAdmin() ? -1 : seat.slot().value();
        int factions = core.queries().config().factions();
        VisibilityIndex visibility = null;
        VisionSystem vision = core.vision();
        if (vision != null && viewerFaction >= 0 && viewerFaction < factions) {
            visibility = vision.visibility(viewerFaction);
        }
        return FogGate.canSee(
                body.matchId(), body.handle(), body.faction(), viewerMatch, viewerFaction, factions, visibility);
    }

    /** A pairing began ({@code seen}) or ended: the player's interest set follows. */
    public static void paired(ServerPlayer player, UnitSpawnData body, boolean seen) {
        StrategosServer server = StrategosServer.getOrNull();
        if (server == null) {
            return;
        }
        if (seen) {
            server.interest().start(player.getUUID(), body.matchId(), body.handle());
        } else {
            server.interest().stop(player.getUUID(), body.matchId(), body.handle());
        }
    }

    private static @Nullable MatchRuntime runtimeOf(StrategosServer server, UnitEntity body) {
        UnitSpawnData data = body.spawnData();
        return data == null || data.matchId() < 1 ? null : server.matches().get(new MatchId(data.matchId()));
    }
}
