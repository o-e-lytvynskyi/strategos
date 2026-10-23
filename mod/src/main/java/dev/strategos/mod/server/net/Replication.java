package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitHud;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.replicate.HudMemory;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.sim.replicate.Visibility;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.FactionStatePayload;
import dev.strategos.mod.server.net.payload.UnitDetailPayload;
import dev.strategos.mod.server.net.payload.UnitHudPayload;
import dev.strategos.mod.server.net.payload.UnitSummaryPayload;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Replication's sender (NET-08, 04 §12.4), after the matches stepped and before the gateway's flush. Tier 2: every
 * {@value #SUMMARY_EVERY} server ticks each running match's factions that have seated players get their summary frame,
 * own units and the enemies {@link #visibility} lets them see. Tier 3: every {@value #HUD_EVERY} ticks each seated
 * player gets the hit points that changed of the units it sees, and every {@value #DETAIL_EVERY} the details of its
 * {@code ui_query} when they changed, own units only; a query comes at most {@value #QUERIES_PER_SECOND} a second.
 * Server thread only.
 */
public final class Replication {
    /** Ticks between two summary frames: 2 Hz. */
    public static final int SUMMARY_EVERY = 10;
    /** 5 Hz. */
    public static final int HUD_EVERY = 4;
    /** 10 Hz. */
    public static final int DETAIL_EVERY = 2;
    /** {@code faction_state} is looked at every 5 ticks and sent to a player when it changed: four a second at most. */
    public static final int FACTION_EVERY = 5;

    public static final int QUERIES_PER_SECOND = 4;

    /** A player's detail subscription: the handles it asked for, what it was last sent, and its recent queries. */
    private static final class Subscription {
        List<Integer> handles = List.of();

        @Nullable UnitDetail sent;

        final ArrayDeque<Long> recent = new ArrayDeque<>();
    }

    private final Map<UUID, HudMemory> huds = new HashMap<>();
    private final Map<UUID, Subscription> subscriptions = new HashMap<>();
    private long queriesRefused;

    private final MinecraftServer server;
    private final Supplier<MatchRegistry> matches;
    private final NetGateway net;
    private final Map<MatchId, Visibility> visibility = new HashMap<>();

    private final Map<UUID, FactionState> factions = new HashMap<>();

    public Replication(MinecraftServer server, Supplier<MatchRegistry> matches, NetGateway net) {
        this.server = server;
        this.matches = matches;
        this.net = net;
    }

    /** Who sees which enemy in match {@code match}; every unit until fog (M6), or a test's own rule for its match. */
    public void visibility(MatchId match, Visibility rule) {
        visibility.put(match, rule);
    }

    private Visibility visibility(MatchRuntime rt) {
        return visibility.getOrDefault(rt.id(), Visibility.ALL);
    }

    /** A seated player's {@code ui_query}: it replaces the last; one past the rate is refused. */
    public void query(ServerPlayer player, MatchRegistry.Seat seat, UiQuery query, long tick) {
        Subscription sub = subscriptions.computeIfAbsent(player.getUUID(), k -> new Subscription());
        while (!sub.recent.isEmpty() && tick - sub.recent.peekFirst() >= 20) {
            sub.recent.removeFirst();
        }
        if (sub.recent.size() >= QUERIES_PER_SECOND) {
            queriesRefused++;
            return;
        }
        sub.recent.addLast(tick);
        sub.handles = query.handles();
        sub.sent = null; // the next detail goes out whatever it holds
    }

    /** Queries refused for their rate, for tests. */
    public long queriesRefused() {
        return queriesRefused;
    }

    /** Forgets a player that left: its hud memory and subscription. */
    public void forget(UUID player) {
        huds.remove(player);
        subscriptions.remove(player);
        factions.remove(player);
    }

    public void tick() {
        int now = server.getTickCount();
        if (now % HUD_EVERY == 0) {
            sendHuds();
        }
        if (now % DETAIL_EVERY == 0) {
            sendDetails();
        }
        if (now % FACTION_EVERY == 0) {
            sendFactions();
        }
        if (now % SUMMARY_EVERY != 0) {
            return;
        }
        visibility.keySet().removeIf(id -> matches.get().get(id) == null); // a disposed match's rule
        for (MatchRuntime rt : matches.get().all()) {
            MatchCore core = rt.core();
            if (core == null || rt.lifecycle() != MatchLifecycle.RUNNING) {
                continue;
            }
            for (int f = 0; f < core.queries().config().factions(); f++) {
                FactionSlot faction = new FactionSlot(f);
                if (rt.roster().of(faction).isEmpty()) {
                    continue;
                }
                net.send(
                        new Audience.Faction(rt.id(), faction),
                        new UnitSummaryPayload(ReplicationService.summary(
                                core.queries(), rt.id().value(), faction, visibility(rt))));
            }
        }
    }

    /**
     * Each seated player's faction state, when it differs from the last one that player got, so a player who just
     * joined gets it at the next look; to that player only, so a faction's state never reaches another (ECO-01.3).
     */
    private void sendFactions() {
        for (MatchRuntime rt : matches.get().all()) {
            MatchCore core = rt.core();
            if (core == null || rt.lifecycle() != MatchLifecycle.RUNNING) {
                continue;
            }
            for (UUID player : rt.roster().members()) {
                MatchRegistry.Seat seat = matches.get().seatOf(player);
                ServerPlayer online = server.getPlayerList().getPlayer(player);
                if (seat == null
                        || seat.match() != rt
                        || online == null
                        || seat.slot().isAdmin()) {
                    continue;
                }
                FactionState now = ReplicationService.factionState(
                        core.queries(),
                        rt.id().value(),
                        new FactionSlot(seat.slot().value()));
                FactionState last = factions.get(player);
                if (last == null || !last.sameAs(now)) {
                    factions.put(player, now);
                    net.send(new Audience.Player(online), new FactionStatePayload(now));
                }
            }
        }
    }

    private void sendHuds() {
        for (MatchRuntime rt : matches.get().all()) {
            MatchCore core = rt.core();
            if (core == null || rt.lifecycle() != MatchLifecycle.RUNNING) {
                continue;
            }
            for (UUID player : rt.roster().members()) {
                MatchRegistry.Seat seat = matches.get().seatOf(player);
                if (seat == null || seat.match() != rt) {
                    continue;
                }
                FactionSlot faction = new FactionSlot(seat.slot().value());
                UnitHud changed = huds.computeIfAbsent(player, k -> new HudMemory())
                        .changes(core.queries(), rt.id().value(), faction, visibility(rt));
                ServerPlayer online = server.getPlayerList().getPlayer(player);
                if (!changed.units().isEmpty() && online != null) {
                    net.send(new Audience.Player(online), new UnitHudPayload(changed));
                }
            }
        }
    }

    private void sendDetails() {
        for (Map.Entry<UUID, Subscription> e : subscriptions.entrySet()) {
            Subscription sub = e.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            MatchRegistry.Seat seat = matches.get().seatOf(e.getKey());
            MatchCore core = seat == null ? null : seat.match().core();
            if (player == null || seat == null || core == null || sub.handles.isEmpty()) {
                continue;
            }
            UnitDetail detail = ReplicationService.detail(
                    core.queries(),
                    seat.match().id().value(),
                    new FactionSlot(seat.slot().value()),
                    sub.handles);
            UnitDetail last = sub.sent;
            if (last == null || !last.units().equals(detail.units())) {
                sub.sent = detail;
                net.send(new Audience.Player(player), new UnitDetailPayload(detail));
            }
        }
    }
}
