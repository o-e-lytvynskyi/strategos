package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.DepositState;
import dev.strategos.core.protocol.FactionState;
import dev.strategos.core.protocol.MatchPresentation;
import dev.strategos.core.protocol.PlayerAuthority;
import dev.strategos.core.protocol.UiQuery;
import dev.strategos.core.protocol.UnitDetail;
import dev.strategos.core.protocol.UnitHud;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.PlayRules;
import dev.strategos.core.sim.diplomacy.DiplomacyView;
import dev.strategos.core.sim.replicate.HudMemory;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.sim.replicate.Visibility;
import dev.strategos.core.sim.vision.VisionDeltaTracker;
import dev.strategos.core.sim.vision.VisionGrid;
import dev.strategos.core.sim.vision.VisionSystem;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.BuildingStatePayload;
import dev.strategos.mod.server.net.payload.DepositStatePayload;
import dev.strategos.mod.server.net.payload.FactionStatePayload;
import dev.strategos.mod.server.net.payload.MatchPresentationPayload;
import dev.strategos.mod.server.net.payload.PlayerAuthorityPayload;
import dev.strategos.mod.server.net.payload.UnitDetailPayload;
import dev.strategos.mod.server.net.payload.UnitHudPayload;
import dev.strategos.mod.server.net.payload.UnitSummaryPayload;
import dev.strategos.mod.server.net.payload.VisionDeltaPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    private final Map<UUID, RecipientContext> contexts = new HashMap<>();
    private final Map<UUID, HudMemory> huds = new HashMap<>();
    private final Map<UUID, Subscription> subscriptions = new HashMap<>();
    private long queriesRefused;

    private final MinecraftServer server;
    private final Supplier<MatchRegistry> matches;
    private final NetGateway net;
    private final PlayerNameTeams nameTeams;
    private final Map<UUID, MatchPresentation> presentations = new HashMap<>();
    private final Map<MatchId, Visibility> visibility = new HashMap<>();
    private final Map<Long, VisionDeltaTracker> visionTrackers = new HashMap<>();

    private final Map<UUID, FactionState> factions = new HashMap<>();
    private final Map<UUID, BuildingState> buildings = new HashMap<>();
    private final Map<UUID, DepositState> deposits = new HashMap<>();

    private record AuthorityMemory(DiplomacyView source, PlayerAuthority frame) {}

    private final Map<UUID, AuthorityMemory> authorities = new HashMap<>();

    public Replication(MinecraftServer server, Supplier<MatchRegistry> matches, NetGateway net) {
        this.server = server;
        this.matches = matches;
        this.net = net;
        this.nameTeams = new PlayerNameTeams(server, net);
    }

    /** A test's own rule for match {@code match}; without one, the faction's {@code VisibilityIndex}. */
    public void visibility(MatchId match, Visibility rule) {
        visibility.put(match, rule);
    }

    /**
     * Default: each faction's {@code VisibilityIndex} (FOW-07 / UI-16). A test override wins. MATCH-07's revealed map
     * returns {@link Visibility#ALL} for every participant.
     */
    private Visibility visibility(MatchRuntime rt) {
        Visibility override = visibility.get(rt.id());
        if (override != null) {
            return override;
        }
        MatchCore core = rt.core();
        if (core != null && core.queries().config().rules().play().revealMap()) {
            return Visibility.ALL;
        }
        VisionSystem vision = core == null ? null : core.vision();
        if (vision == null) {
            return Visibility.ALL;
        }
        return (viewer, unit) -> vision.visibility(viewer.value()).isVisible(unit.handle());
    }

    /** A seated player's {@code ui_query}: it replaces the last; one past the rate is refused. */
    public void query(ServerPlayer player, MatchRegistry.Seat seat, UiQuery query, long tick) {
        MatchRegistry registry = matches.get();
        MatchRegistry.Seat current = registry.seatOf(player.getUUID());
        ServerPlayer online = server.getPlayerList().getPlayer(player.getUUID());
        RecipientContext origin = RecipientContext.capture(player, registry);
        if (!seat.hasPerspective()
                || current == null
                || current.match() != seat.match()
                || !current.slot().equals(seat.slot())
                || online == null
                || !origin.current(online, registry)) {
            net.rejected(player, IngressReason.UI_QUERY_UNAVAILABLE);
            return;
        }
        refreshRecipient(player, registry);
        Subscription sub = subscriptions.computeIfAbsent(player.getUUID(), k -> new Subscription());
        while (!sub.recent.isEmpty() && tick - sub.recent.peekFirst() >= 20) {
            sub.recent.removeFirst();
        }
        if (sub.recent.size() >= QUERIES_PER_SECOND) {
            queriesRefused++;
            net.rejected(player, IngressReason.UI_QUERY_RATE);
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
        contexts.remove(player);
        huds.remove(player);
        authorities.remove(player);
        presentations.remove(player);
        nameTeams.forget(player);
        subscriptions.remove(player);
        factions.remove(player);
        buildings.remove(player);
        deposits.remove(player);
    }

    /** Reset every recipient-specific memory before producing from a new connection, seat or full view. */
    private void refreshRecipients() {
        MatchRegistry registry = matches.get();
        var live = new HashSet<UUID>();
        for (MatchRuntime rt : registry.all()) {
            for (UUID member : rt.roster().members()) {
                ServerPlayer player = server.getPlayerList().getPlayer(member);
                MatchRegistry.Seat seat = registry.seatOf(member);
                if (player == null
                        || seat == null
                        || seat.match() != rt
                        || rt.roster().generationOf(member) == 0) continue;
                live.add(member);
                refreshRecipient(player, registry);
            }
        }
        for (UUID member : List.copyOf(contexts.keySet())) {
            if (!live.contains(member)) forget(member);
        }
    }

    private void refreshRecipient(ServerPlayer player, MatchRegistry registry) {
        UUID member = player.getUUID();
        RecipientContext before = contexts.get(member);
        if (before != null && before.current(player, registry)) return;
        forget(member);
        RecipientContext fresh = RecipientContext.capture(player, registry);
        contexts.put(member, fresh);
        MatchRegistry.Seat seat = fresh.seat();
        if (seat != null && fresh.generation() != 0 && fresh.primary() >= 0) {
            // The delta tracker is per faction; a joining co-op primary needs a full initial bitmap too.
            visionTrackers.remove(((long) seat.match().id().value() << 8) | fresh.primary());
        }
    }

    /** A pause freezes simulation, not a member's initial/recipient snapshot delivery. */
    private static boolean publishesSnapshots(MatchRuntime rt) {
        MatchLifecycle lifecycle = rt.lifecycle();
        return lifecycle == MatchLifecycle.RUNNING || lifecycle == MatchLifecycle.PAUSED;
    }

    public void tick() {
        refreshRecipients();
        sendAuthorities();
        sendPresentations();
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
        if (now % SUMMARY_EVERY == 0) {
            sendBuildings();
            sendDeposits();
        }
        if (now % SUMMARY_EVERY != 0) {
            return;
        }
        visibility.keySet().removeIf(id -> matches.get().get(id) == null);
        visionTrackers.keySet().removeIf(key -> matches.get().get(new MatchId((int) (key >> 8))) == null);
        for (MatchRuntime rt : matches.get().all()) {
            MatchCore core = rt.core();
            if (core == null || !publishesSnapshots(rt)) {
                continue;
            }
            VisionSystem vision = core.vision();
            for (int f = 0; f < core.queries().config().factions(); f++) {
                FactionSlot faction = new FactionSlot(f);
                if (rt.roster().of(faction, core.queries().diplomacy()).isEmpty()) {
                    continue;
                }
                net.send(
                        new Audience.Faction(rt.id(), faction),
                        new UnitSummaryPayload(ReplicationService.summary(
                                core.queries(), rt.id().value(), faction, visibility(rt))));
                if (vision != null) {
                    long trackerKey = ((long) rt.id().value() << 8) | f;
                    var grid = vision.grid(f);
                    VisionDeltaTracker tracker = visionTrackers.computeIfAbsent(
                            trackerKey, k -> new VisionDeltaTracker(grid.cellsX(), grid.cellsZ()));
                    net.send(
                            new Audience.Faction(rt.id(), faction),
                            new VisionDeltaPayload(tracker.delta(
                                    rt.id().value(), core.queries().tick(), grid)));
                }
            }
        }
    }

    private void sendPresentations() {
        var recipients = new java.util.HashSet<UUID>();
        for (MatchRuntime match : matches.get().all()) {
            if (match.core() == null) continue;
            MatchPresentation frame = net.presentation(match);
            for (UUID member : match.roster().members()) {
                var seat = matches.get().seatOf(member);
                ServerPlayer online = server.getPlayerList().getPlayer(member);
                if (seat == null
                        || !seat.match().equals(match)
                        || !seat.hasPerspective()
                        || online == null
                        || online.hasDisconnected()) continue;
                recipients.add(member);
                MatchPresentation last = presentations.get(member);
                if (last == null || !last.sameView(frame)) {
                    net.send(new Audience.Player(online), new MatchPresentationPayload(frame));
                    presentations.put(member, frame);
                }
                nameTeams.update(online, frame);
            }
        }
        nameTeams.retain(recipients);
        presentations.keySet().retainAll(recipients);
    }

    /** Unchanged diplomacy avoids rebuilding permission masks every tick. */
    private void sendAuthorities() {
        for (MatchRuntime rt : matches.get().all()) {
            MatchCore core = rt.core();
            if (core == null) continue;
            DiplomacyView source = core.queries().diplomacy();
            for (UUID player : rt.roster().members()) {
                var seat = matches.get().seatOf(player);
                ServerPlayer online = server.getPlayerList().getPlayer(player);
                if (seat == null || seat.match() != rt || online == null || !seat.hasPerspective()) continue;
                AuthorityMemory last = authorities.get(player);
                if (last != null
                        && last.source().equals(source)
                        && last.frame().matchId() == rt.id().value()
                        && last.frame().player().equals(seat.slot())) continue;
                var next = seat.authority();
                if (next.isEmpty()) continue;
                PlayerAuthority frame = next.orElseThrow();
                authorities.put(player, new AuthorityMemory(source, frame));
                if (last == null || !last.frame().sameAuthority(frame)) {
                    net.send(new Audience.Player(online), new PlayerAuthorityPayload(frame));
                }
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
            if (core == null || !publishesSnapshots(rt)) {
                continue;
            }
            for (UUID player : rt.roster().members()) {
                MatchRegistry.Seat seat = matches.get().seatOf(player);
                ServerPlayer online = server.getPlayerList().getPlayer(player);
                if (seat == null
                        || seat.match() != rt
                        || !seat.hasPerspective()
                        || online == null
                        || seat.slot().isAdmin()) {
                    continue;
                }
                FactionState now = ReplicationService.factionState(
                        core.queries(), rt.id().value(), seat.primaryFaction().orElseThrow());
                FactionState last = factions.get(player);
                if (last == null || !last.sameAs(now)) {
                    factions.put(player, now);
                    net.send(new Audience.Player(online), new FactionStatePayload(now));
                }
            }
        }
    }

    /**
     * Each seated player's buildings (BLD-01), when they differ from the last the player got, at the summaries' pace:
     * its own faction's with the owner's fields, the others' without them. Under fog, unexplored enemy buildings are
     * hidden (MATCH-07); a pristine grid still sends every live building. The first frame includes empty tables.
     */
    private void sendBuildings() {
        for (MatchRuntime rt : matches.get().all()) {
            MatchCore core = rt.core();
            if (core == null || !publishesSnapshots(rt)) {
                continue;
            }
            for (UUID player : rt.roster().members()) {
                MatchRegistry.Seat seat = matches.get().seatOf(player);
                ServerPlayer online = server.getPlayerList().getPlayer(player);
                if (seat == null || seat.match() != rt || !seat.hasPerspective() || online == null) {
                    continue;
                }
                FactionSlot view = viewer(seat);
                BuildingState live =
                        ReplicationService.buildingState(core.queries(), rt.id().value(), view);
                BuildingState now = fogBuildings(core, view, live);
                BuildingState last = buildings.get(player);
                if (last == null || !last.sameAs(now)) {
                    buildings.put(player, now);
                    net.send(new Audience.Player(online), new BuildingStatePayload(now));
                }
            }
        }
    }

    /**
     * Hide enemy buildings whose anchor cell the viewer has never explored. Own buildings and a revealed map keep the
     * full list; a pristine grid (nothing explored yet) keeps today's send-all behaviour.
     */
    static BuildingState fogBuildings(MatchCore core, @Nullable FactionSlot viewer, BuildingState live) {
        if (viewer == null || live.buildings().isEmpty()) {
            return live;
        }
        PlayRules play = core.queries().config().rules().play();
        if (play.revealMap() || play.explored()) {
            return live;
        }
        VisionSystem vision = core.vision();
        if (vision == null) {
            return live;
        }
        VisionGrid grid = vision.grid(viewer.value());
        if (grid.pristine()) {
            return live;
        }
        List<BuildingState.Entry> out = new ArrayList<>(live.buildings().size());
        for (BuildingState.Entry e : live.buildings()) {
            if (e.faction() == viewer.value() || grid.isExplored(VisionGrid.cellOf(e.x()), VisionGrid.cellOf(e.z()))) {
                out.add(e);
            }
        }
        return out.size() == live.buildings().size() ? live : new BuildingState(live.matchId(), live.tick(), out);
    }

    /** Each seated player's deposits (ECO-08), when they differ from the last the player got, at the summaries' pace. */
    private void sendDeposits() {
        for (MatchRuntime rt : matches.get().all()) {
            MatchCore core = rt.core();
            if (core == null || !publishesSnapshots(rt)) {
                continue;
            }
            DepositState now =
                    ReplicationService.depositState(core.queries(), rt.id().value());
            for (UUID player : rt.roster().members()) {
                MatchRegistry.Seat seat = matches.get().seatOf(player);
                ServerPlayer online = server.getPlayerList().getPlayer(player);
                if (seat == null || seat.match() != rt || !seat.hasPerspective() || online == null) {
                    continue;
                }
                DepositState last = deposits.get(player);
                if (last == null || !last.sameAs(now)) {
                    deposits.put(player, now);
                    net.send(new Audience.Player(online), new DepositStatePayload(now));
                }
            }
        }
    }

    private void sendHuds() {
        for (MatchRuntime rt : matches.get().all()) {
            MatchCore core = rt.core();
            if (core == null || !publishesSnapshots(rt)) {
                continue;
            }
            for (UUID player : rt.roster().members()) {
                MatchRegistry.Seat seat = matches.get().seatOf(player);
                if (seat == null || seat.match() != rt || !seat.hasPerspective()) {
                    continue;
                }
                UnitHud changed = huds.computeIfAbsent(player, k -> new HudMemory())
                        .changes(core.queries(), rt.id().value(), viewer(seat), visibility(rt));
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
            if (player == null || seat == null || core == null || !seat.hasPerspective() || sub.handles.isEmpty()) {
                continue;
            }
            UnitDetail detail = ReplicationService.detail(
                    core.queries(), seat.match().id().value(), viewer(seat), sub.handles, visibility(seat.match()));
            UnitDetail last = sub.sent;
            if (last == null || !last.units().equals(detail.units())) {
                sub.sent = detail;
                net.send(new Audience.Player(player), new UnitDetailPayload(detail));
            }
        }
    }

    /** The faction a seat views as; none for an observer (the admin's slot), who sees every faction. */
    private static @Nullable FactionSlot viewer(MatchRegistry.Seat seat) {
        return seat.slot().isAdmin() ? null : seat.primaryFaction().orElseThrow();
    }
}
