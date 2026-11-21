package dev.strategos.mod.server.net;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.CanonicalJson;
import dev.strategos.core.protocol.AllClear;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.AttackSpread;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.DebugFeedMsg;
import dev.strategos.core.protocol.FactionLook;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.FeedbackMsg;
import dev.strategos.core.protocol.Follow;
import dev.strategos.core.protocol.Fx;
import dev.strategos.core.protocol.Garrison;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchPresentation;
import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.PlayerAuthority;
import dev.strategos.core.protocol.RingBell;
import dev.strategos.core.protocol.SetStance;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.Ungarrison;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.core.sim.replicate.Visibility;
import dev.strategos.core.sim.vision.VisionGrid;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.registry.ModPayloads;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.DebugFeedPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.FxPayload;
import dev.strategos.mod.server.net.payload.GroupRestorePayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.MatchPresentationPayload;
import dev.strategos.mod.server.net.payload.MatchStatePayload;
import dev.strategos.mod.server.net.payload.PlayerAuthorityPayload;
import dev.strategos.mod.server.tick.FaultBarrier;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The only sender (ADR-0016, rule B6). A payload goes to an audience its type declares; to players it is queued and
 * flushed once a tick ({@code ServerTickEvent.Post}), all of a player's in one bundle, and a player whose client lacks
 * our channel, such as a vanilla client, gets none; to a body's trackers it goes at once, through vanilla's tracking.
 * Every payload's record bytes are counted per type and player. It also reads the {@code commands} clients send:
 * refused batches and senders with no seat are violations, and the 101st in a minute disconnects (spec §6.5); a seated
 * sender's commands carry its slot as issuer and go to its match inside the match's fault barrier, and each result goes
 * back to it as {@code feedback}. Members of a match get its {@code match_state} on every change of its lifecycle and
 * every 5 seconds, and when a faction's equipment tiers change (TECH-03), which keeps their clients' clock of the match
 * in step (04 §12.2).
 */
public final class NetGateway {
    private final Map<Integer, MatchPresentation> publicPresentations = new HashMap<>();
    /** The reason a player hears when it is disconnected for its violations. */
    public static final String TOO_MANY = "strategos: too many invalid packets";

    /** Ticks between two {@code match_state}s of a match whose lifecycle did not change: the clock sync. */
    private static final int STATE_EVERY = 100;
    /**
     * What an Ungarrison naming no unit (everyone inside), a bell or an all clear counts: the civic centre's capacity,
     * the largest holder.
     */
    static final int EVERYONE_INSIDE = 20;

    private final MinecraftServer server;
    private final Supplier<MatchRegistry> matches;
    private final FaultBarrier faults;

    private record QueuedFrame(CustomPacketPayload payload, RecipientContext recipient) {}

    private record SequencedRecipient(RecipientContext origin, CommandSequenceWindow window) {}

    private final Map<UUID, SequencedRecipient> commandSequences = new HashMap<>();

    private record WireView(long epoch, RecipientContext origin) {}

    private final Map<UUID, WireView> wireViews = new HashMap<>();
    // A fresh boot namespace prevents an old client's retained epoch from being reused after a server restart.
    // The counter then has at least 2^62 increments of positive headroom; neither value is game state.
    private long nextEpoch = new SecureRandom().nextLong() & 0x3fff_ffff_ffff_ffffL;

    private final Map<UUID, List<QueuedFrame>> queued = new LinkedHashMap<>();
    private final Map<Integer, MatchLifecycle> lastState = new HashMap<>();
    private final Map<Integer, List<FactionLook>> lastLooks = new HashMap<>();
    private final NetCounters counters = new NetCounters();
    private final RateLimiter limiter = new RateLimiter();
    private final RateLimiter auxiliaryLimiter = new RateLimiter();

    public NetGateway(MinecraftServer server, Supplier<MatchRegistry> matches, FaultBarrier faults) {
        this.server = server;
        this.matches = matches;
        this.faults = faults;
    }

    /** Sends {@code payload} to {@code audience}; a type that does not declare the audience is refused. */
    public void send(Audience audience, CustomPacketPayload payload) {
        check(ModPayloads.spec(payload.type().id()), payload.type().id(), audience.kind());
        switch (audience) {
            case Audience.Player p -> queue(p.player(), payload);
            case Audience.TrackersOf t -> toTrackers(t, payload);
            case Audience.Faction f -> {
                MatchRuntime rt = matches.get().get(f.match());
                if (rt != null) {
                    MatchCore core = rt.core();
                    if (core != null)
                        rt.roster()
                                .of(f.faction(), core.queries().diplomacy())
                                .forEach(player -> queue(player, payload));
                }
            }
            case Audience.SeesCell c -> toVisibleEvents(c.match(), payload, false);
            case Audience.SeesEither e -> toVisibleEvents(e.match(), payload, true);
            case Audience.Match m -> toMembers(m.match(), payload);
        }
    }

    /**
     * Sends {@code payload} to {@code player} at once rather than with the tick's flush: the template set, which must
     * reach a joining player before any body it could pair (DATA-03.6).
     */
    public void sendNow(ServerPlayer player, CustomPacketPayload payload) {
        check(ModPayloads.spec(payload.type().id()), payload.type().id(), Audience.Kind.PLAYER);
        if (player.connection.hasChannel(payload)) {
            counters.sent(payload.type().id(), player.getGameProfile().getName(), size(payload));
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * A body's hands changed (ECO-05): its trackers get vanilla's equipment packet at once, the tool in the main hand and
     * the load in the off hand, as {@code LivingEntity#detectEquipmentUpdates} would send it if a body's server tick ran.
     * Vanilla's tracking is the audience, so the fog gate that decides who tracks a body decides who sees them.
     */
    public void equipment(UnitEntity body, ItemStack tool, ItemStack load) {
        if (body.level() instanceof ServerLevel level && body.isAddedToLevel()) {
            level.getChunkSource()
                    .broadcast(
                            body,
                            new ClientboundSetEquipmentPacket(
                                    body.getId(),
                                    List.of(
                                            Pair.of(EquipmentSlot.MAINHAND, tool.copy()),
                                            Pair.of(EquipmentSlot.OFFHAND, load.copy()))));
        }
    }

    /**
     * Tells {@code player}, just seated in {@code rt} at {@code slot}, which match it is in ({@code match_join}); the
     * next flush sends the match's state to its members, the player among them, as {@code match_state} goes only to a
     * match.
     */
    public void joined(MatchRuntime rt, ServerPlayer player, PlayerSlot slot) {
        MatchCore core = rt.core();
        if (core == null) {
            return;
        }
        MatchRegistry registry = matches.get();
        MatchRegistry.Seat current = registry.seatOf(player.getUUID());
        if (current == null
                || current.match() != rt
                || !current.slot().equals(slot)
                || !rt.roster().isConnected(player.getUUID())) return;
        ServerPlayer online = server.getPlayerList().getPlayer(player.getUUID());
        RecipientContext origin = RecipientContext.capture(player, registry);
        if (online == null || !origin.current(online, registry)) return;
        var authority = current.authority();
        if (authority.isEmpty()) return;
        registry.renewRecipient(player.getUUID());
        long epoch = Math.incrementExact(nextEpoch);
        nextEpoch = epoch;
        wireViews.put(player.getUUID(), new WireView(epoch, RecipientContext.capture(player, registry)));
        MatchSpace space = rt.space();
        String rules = CanonicalJson.write(MatchRules.CODEC
                .encodeStart(JsonOps.INSTANCE, core.queries().config().rules())
                .getOrThrow());
        send(
                new Audience.Player(player),
                new MatchJoinPayload(
                        epoch,
                        new MatchJoin(
                                rt.id().value(),
                                space.origin().getX(),
                                space.origin().getY(),
                                space.origin().getZ(),
                                space.sizeX(),
                                space.sizeZ(),
                                space.minY(),
                                space.maxY(),
                                core.queries().config().factions(),
                                slot,
                                rules,
                                authority.orElseThrow())));
        send(new Audience.Player(player), new MatchPresentationPayload(presentation(rt)));
        lastState.remove(rt.id().value());
        lastLooks.remove(rt.id().value());
    }

    /** Fresh private view validation; no client-supplied cache can create command authority. */
    public void restoreGroups(ServerPlayer player, MatchRegistry.Seat seat, Visibility visibility) {
        WireView view = wireViews.get(player.getUUID());
        MatchCore core = seat.match().core();
        MatchRegistry registry = matches.get();
        MatchRegistry.Seat current = registry.seatOf(player.getUUID());
        if (view == null
                || core == null
                || current == null
                || current.match() != seat.match()
                || !current.slot().equals(seat.slot())
                || !view.origin().current(player, registry)) return;
        var authority = seat.authority();
        if (authority.isEmpty()) return;
        send(
                new Audience.Player(player),
                new GroupRestorePayload(
                        GroupRestoreFrame.capture(view.epoch(), authority.orElseThrow(), core.queries(), visibility)));
    }

    /** Adapter changes keep ordering even while the simulation is paused. */
    MatchPresentation presentation(MatchRuntime match) {
        MatchPresentation next = PublicPresentation.of(server, match);
        MatchPresentation previous = publicPresentations.get(match.id().value());
        if (previous != null && previous.sameView(next)) return previous;
        long revision = previous == null ? 1 : Math.addExact(previous.revision(), 1);
        var published = new MatchPresentation(next.matchId(), revision, next.factions(), next.participants());
        publicPresentations.put(match.id().value(), published);
        return published;
    }

    /** Bounded player-name team packets; the caller owns an isolated board, never a global broadcast. */
    void sendPlayerPacket(ServerPlayer player, Packet<?> packet) {
        player.connection.send(packet);
    }

    /** Refuses a payload type the server does not send ({@code spec} null or client-to-server) or an audience it lacks. */
    public static void check(@Nullable PayloadSpec spec, ResourceLocation id, Audience.Kind audience) {
        if (spec == null || !spec.toClient()) {
            throw new IllegalArgumentException(id + " is not a payload the server sends");
        }
        if (!spec.audiences().contains(audience)) {
            throw new IllegalArgumentException(id + " may not go to " + audience);
        }
    }

    /**
     * Sends every queued payload, a player's together; once a tick, after the matches have stepped. A match's members
     * first get its {@code match_state} when its lifecycle changed, and every {@value #STATE_EVERY} ticks.
     */
    public void flush() {
        int tick = server.getTickCount();
        MatchRegistry registry = matches.get();
        commandSequences.entrySet().removeIf(entry -> {
            ServerPlayer online = server.getPlayerList().getPlayer(entry.getKey());
            return online == null || !entry.getValue().origin().current(online, registry);
        });
        wireViews.entrySet().removeIf(entry -> {
            ServerPlayer online = server.getPlayerList().getPlayer(entry.getKey());
            return online == null || !entry.getValue().origin().current(online, registry);
        });
        Map<Integer, MatchLifecycle> now = new HashMap<>();
        Map<Integer, List<FactionLook>> nowLooks = new HashMap<>();
        for (MatchRuntime rt : matches.get().all()) {
            now.put(rt.id().value(), rt.lifecycle());
            MatchCore core = rt.core();
            List<FactionLook> looks = core == null ? List.of() : core.queries().factionLooks();
            nowLooks.put(rt.id().value(), looks);
            if (lastState.get(rt.id().value()) != rt.lifecycle()
                    || !looks.equals(lastLooks.get(rt.id().value()))
                    || tick % STATE_EVERY == 0) {
                send(new Audience.Match(rt.id()), state(rt, looks));
            }
        }
        publicPresentations.keySet().retainAll(now.keySet());
        lastState.clear();
        lastState.putAll(now); // a disposed match is forgotten
        lastLooks.clear();
        lastLooks.putAll(nowLooks);
        for (Map.Entry<UUID, List<QueuedFrame>> e : queued.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                continue; // it left this tick
            }
            List<CustomPacketPayload> sendable = new ArrayList<>();
            for (QueuedFrame frame : e.getValue()) {
                if (!frame.recipient().current(player, matches.get())) continue;
                CustomPacketPayload p = frame.payload();
                if (player.connection.hasChannel(p)) {
                    sendable.add(p);
                    counters.sent(p.type().id(), player.getGameProfile().getName(), size(p));
                }
            }
            if (!sendable.isEmpty()) {
                PacketDistributor.sendToPlayer(
                        player,
                        sendable.get(0),
                        sendable.subList(1, sendable.size()).toArray(new CustomPacketPayload[0]));
            }
        }
        queued.clear();
    }

    /** A lifecycle notification may initialize modes only for the current open transport; it is not a packet violation. */
    public boolean acceptsLogin(ServerPlayer player) {
        return currentTransport(player, player.connection.getConnection());
    }

    /** Logout may follow a closed transport, but never owns a newer player's UUID-scoped state. */
    public boolean acceptsLogout(ServerPlayer player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player.getUUID());
        return online == null || RecipientContext.samePlayer(online, player);
    }

    /** The live transport may speak before match admission; freshness alone confers no match authority. */
    public boolean acceptsSender(ServerPlayer player, Connection transport) {
        if (currentTransport(player, transport)) return true;
        // Do not let an old handler spend the replacement connection's rate or kick budget.
        counters.violation(player.getGameProfile().getName(), IngressReason.STALE_CONNECTION);
        return false;
    }

    private boolean currentTransport(ServerPlayer player, Connection transport) {
        ServerPlayer online = server.getPlayerList().getPlayer(player.getUUID());
        return online != null && RecipientContext.capture(player, matches.get()).connectionCurrent(online, transport);
    }

    /** A wire epoch authorizes only the exact currently authenticated full view, never a later runtime or seat. */
    public boolean acceptsEpoch(ServerPlayer player, long epoch) {
        WireView view = wireViews.get(player.getUUID());
        ServerPlayer online = server.getPlayerList().getPlayer(player.getUUID());
        if (epoch > 0
                && view != null
                && view.epoch() == epoch
                && online != null
                && view.origin().current(online, matches.get())) return true;
        if (epoch <= 0) refused(player, IngressReason.STALE_MATCH_EPOCH);
        else rejected(player, IngressReason.STALE_MATCH_EPOCH);
        return false;
    }

    /** A batch dispatched after the registered handler's epoch gate, on the server thread. */
    public void commands(ServerPlayer player, CommandsPayload payload) {
        MatchRegistry registry = matches.get();
        RecipientContext origin = RecipientContext.capture(player, registry);
        ServerPlayer online = server.getPlayerList().getPlayer(player.getUUID());
        if (online == null || !origin.current(online, registry)) {
            // A stale handler must not mutate the new seat or spend the replacement's violation budget.
            counters.violation(player.getGameProfile().getName(), IngressReason.STALE_CONNECTION);
            return;
        }
        long tick = server.getTickCount();
        CommandBatch batch = payload.batch();
        if (batch == null) {
            refuse(player, tick, IngressReason.UNREADABLE);
            return;
        }
        int handles = 0;
        for (Command command : batch.commands()) {
            handles += handles(command);
        }
        if (!limiter.take(player.getUUID(), tick, batch.commands().size(), handles)) {
            refuse(player, tick, IngressReason.COMMAND_RATE);
            return;
        }
        MatchRegistry.Seat seat = matches.get().seatOf(player.getUUID());
        if (seat == null) {
            refuse(player, tick, IngressReason.UNSEATED);
            return;
        }
        if (seat.slot().isAdmin()) {
            // the admin's slot seats watchers, whose commands would rule every faction: dropped, not a fault
            counters.violation(player.getGameProfile().getName(), IngressReason.OBSERVER);
            return;
        }
        MatchRuntime rt = seat.match();
        if (rt.lifecycle() != MatchLifecycle.RUNNING && rt.lifecycle() != MatchLifecycle.PAUSED) {
            counters.violation(player.getGameProfile().getName(), IngressReason.INACTIVE_MATCH); // dropped, not a fault
            return;
        }
        SequencedRecipient sequenced = commandSequences.get(player.getUUID());
        if (sequenced == null || !sequenced.origin().current(player, registry)) {
            sequenced = new SequencedRecipient(origin, new CommandSequenceWindow());
        }
        if (!sequenced.window().take(batch.clientSeq(), batch.commands().size())) {
            refuse(player, tick, IngressReason.COMMAND_SEQUENCE);
            return;
        }
        commandSequences.put(player.getUUID(), sequenced);
        faults.run(rt, "commands", () -> {
            for (int k = 0; k < batch.commands().size(); k++) {
                int seq = batch.clientSeq() + k;
                rt.submit(seat.slot(), batch.commands().get(k), seq, result -> feedback(origin, seq, result));
            }
        });
    }

    /** A command's result for the player who sent it, if it is still here. */
    private void feedback(RecipientContext origin, int seq, CommandResult result) {
        ServerPlayer online = server.getPlayerList().getPlayer(origin.player().getUUID());
        if (online != null && origin.current(online, matches.get())) {
            send(
                    new Audience.Player(online),
                    new FeedbackPayload(new Feedback(
                            seq,
                            result.outcome(),
                            result.firstReason(),
                            result.accepted(),
                            result.accepted() + result.rejected(),
                            result.missing())));
        }
    }

    public NetCounters counters() {
        return counters;
    }

    /** Drop unsent match data during preparation rollback without resetting the incoming violation budget. */
    public void discardQueued(UUID player) {
        queued.remove(player);
    }

    /** A player who left starts afresh. */
    public void forget(ServerPlayer player) {
        commandSequences.remove(player.getUUID());
        wireViews.remove(player.getUUID());
        limiter.forget(player.getUUID());
        auxiliaryLimiter.forget(player.getUUID());
        queued.remove(player.getUUID());
    }

    /** Lobby intents use the auxiliary budget, independently of simulation commands and handle costs. */
    public boolean allowLobbyIntent(ServerPlayer player) {
        return takeAuxiliaryIntent(player, IngressReason.LOBBY_RATE);
    }

    /** A well-formed auxiliary request, before its type-specific permission/rate/state checks. */
    public boolean allowAuxiliaryIntent(ServerPlayer player) {
        return takeAuxiliaryIntent(player, IngressReason.AUXILIARY_RATE);
    }

    private boolean takeAuxiliaryIntent(ServerPlayer player, IngressReason reason) {
        if (auxiliaryLimiter.take(player.getUUID(), server.getTickCount(), 1, 0)) return true;
        refused(player, reason);
        return false;
    }

    /** A normal unavailable action is audited without charging the protocol kick budget. */
    public void rejected(ServerPlayer player, IngressReason reason) {
        counters.rejected(player.getGameProfile().getName(), reason);
    }

    /** Another handler refused what {@code player} sent: a violation, counted as the gateway's own are. */
    public void refused(ServerPlayer player, IngressReason reason) {
        refuse(player, server.getTickCount(), reason);
    }

    private void refuse(ServerPlayer player, long tick, IngressReason reason) {
        counters.violation(player.getGameProfile().getName(), reason);
        if (limiter.violation(player.getUUID(), tick)) {
            limiter.forget(player.getUUID());
            auxiliaryLimiter.forget(player.getUUID());
            player.connection.disconnect(Component.literal(TOO_MANY));
        }
    }

    private void queue(UUID player, CustomPacketPayload payload) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) queue(online, payload);
    }

    private void queue(ServerPlayer player, CustomPacketPayload payload) {
        RecipientContext context = RecipientContext.capture(player, matches.get());
        queued.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(new QueuedFrame(payload, context));
    }

    /** Project each event, not its coarse batching page; a control grant never grants vision. */
    private void toVisibleEvents(MatchId match, CustomPacketPayload payload, boolean eitherEnd) {
        if (!(payload instanceof FxPayload frame)) {
            throw new IllegalArgumentException("visibility audiences require an fx payload");
        }
        if (frame.fx().matchId() != match.value()) {
            throw new IllegalArgumentException("fx audience and match differ");
        }
        MatchRegistry registry = matches.get();
        MatchRuntime rt = registry.get(match);
        if (rt == null) return;
        MatchCore core = rt.core();
        if (core == null) return;
        boolean revealed = core.queries().config().rules().play().revealMap();
        var vision = core.vision();
        for (UUID player : rt.roster().members()) {
            MatchRegistry.Seat seat = registry.seatOf(player);
            if (seat == null || seat.match() != rt) continue;
            if (seat.slot().isAdmin() || revealed) {
                queue(player, payload);
                continue;
            }
            var primary = seat.primaryFaction();
            if (primary.isEmpty() || vision == null) continue;
            VisionGrid grid = vision.grid(primary.orElseThrow().value());
            List<Fx.Event> visible = frame.fx().events().stream()
                    .filter(event -> seesEvent(grid, event, eitherEnd))
                    .toList();
            if (!visible.isEmpty()) {
                queue(player, new FxPayload(new Fx(match.value(), visible)));
            }
        }
    }

    private static boolean seesEvent(VisionGrid grid, Fx.Event event, boolean eitherEnd) {
        // Protocol coordinates are eighths of a block; vision cells are four blocks, not 16-block FX pages.
        return grid.count(event.x8() >> 5, event.z8() >> 5) > 0
                || (eitherEnd && event.kind().carriesPath() && grid.count(event.toX8() >> 5, event.toZ8() >> 5) > 0);
    }

    private void toMembers(MatchId match, CustomPacketPayload payload) {
        MatchRuntime rt = matches.get().get(match);
        if (rt != null) {
            rt.roster().members().forEach(player -> queue(player, payload));
        }
    }

    private static MatchStatePayload state(MatchRuntime rt, List<FactionLook> looks) {
        MatchStateMsg.Lifecycle lifecycle = switch (rt.lifecycle()) {
            case CREATED -> MatchStateMsg.Lifecycle.CREATED;
            case PREPARING -> MatchStateMsg.Lifecycle.PREPARING;
            case READY -> MatchStateMsg.Lifecycle.READY;
            case RUNNING -> MatchStateMsg.Lifecycle.RUNNING;
            case PAUSED -> MatchStateMsg.Lifecycle.PAUSED;
            case ENDED -> MatchStateMsg.Lifecycle.ENDED;
            case FAULTED -> MatchStateMsg.Lifecycle.FAULTED;
            case DISPOSED -> MatchStateMsg.Lifecycle.DISPOSED;
        };
        var core = rt.core();
        var results = lifecycle == MatchStateMsg.Lifecycle.ENDED && core != null
                ? core.queries().matchResults()
                : null;
        return new MatchStatePayload(
                new MatchStateMsg(rt.id().value(), lifecycle, Math.max(0, rt.tick()), results, looks));
    }

    private void toTrackers(Audience.TrackersOf trackers, CustomPacketPayload payload) {
        MatchRuntime rt = matches.get().get(trackers.match());
        UnitEntity body = rt == null ? null : rt.bodies().get(trackers.handle());
        if (body != null) {
            PacketDistributor.sendToPlayersTrackingEntity(body, payload);
            counters.sent(payload.type().id(), "trackers", size(payload));
        }
    }

    /** The units a command names, which the rate limiter counts (security.md): every unit-set command counts. */
    static int handles(Command command) {
        return switch (command) {
            case Move move -> move.units().size();
            case Stop stop -> stop.units().size();
            case Attack attack -> attack.units().size();
            case AttackMove move -> move.units().size();
            case Follow follow -> follow.units().size();
            case AttackSpread spread -> spread.units().size();
            case SetStance stance -> stance.units().size();
            case Garrison garrison -> garrison.units().size();
            case Ungarrison ungarrison ->
                ungarrison.units().size() == 0
                        ? EVERYONE_INSIDE
                        : ungarrison.units().size();
            case RingBell bell -> EVERYONE_INSIDE; // every worker within reach, as many as a holder takes
            case AllClear clear -> EVERYONE_INSIDE;
            default -> 0;
        };
    }

    /** A payload's record bytes, as the wire carries them. */
    static int size(CustomPacketPayload payload) {
        return switch (payload) {
            case MatchPresentationPayload p -> WireCodec.encode(MatchPresentation.WIRE, p.presentation()).length;
            case GroupRestorePayload p -> WireCodec.encode(GroupRestoreFrame.WIRE, p.frame()).length;
            case MatchJoinPayload p -> Long.BYTES + WireCodec.encode(MatchJoin.WIRE, p.join()).length;
            case PlayerAuthorityPayload p -> WireCodec.encode(PlayerAuthority.WIRE, p.authority()).length;
            case MatchStatePayload p -> WireCodec.encode(MatchStateMsg.WIRE, p.state()).length;
            case FeedbackPayload p -> WireCodec.encode(FeedbackMsg.WIRE, p.message()).length;
            case DebugFeedPayload p -> WireCodec.encode(DebugFeedMsg.WIRE, p.message()).length;
            case FxPayload p -> WireCodec.encode(Fx.WIRE, p.fx()).length;
            default -> 0;
        };
    }
}
