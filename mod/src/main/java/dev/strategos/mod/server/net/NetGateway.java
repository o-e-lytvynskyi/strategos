package dev.strategos.mod.server.net;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import dev.strategos.core.content.CanonicalJson;
import dev.strategos.core.protocol.Attack;
import dev.strategos.core.protocol.AttackMove;
import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.DebugFeedMsg;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.FeedbackMsg;
import dev.strategos.core.protocol.Follow;
import dev.strategos.core.protocol.Fx;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.MatchId;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.core.sim.CommandResult;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.MatchRules;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.registry.ModPayloads;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.DebugFeedPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.FxPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.MatchStatePayload;
import dev.strategos.mod.server.tick.FaultBarrier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
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
 * every 5 seconds, which keeps their clients' clock of the match in step (04 §12.2).
 */
public final class NetGateway {
    /** The reason a player hears when it is disconnected for its violations. */
    public static final String TOO_MANY = "strategos: too many invalid packets";

    /** Ticks between two {@code match_state}s of a match whose lifecycle did not change: the clock sync. */
    private static final int STATE_EVERY = 100;

    private final MinecraftServer server;
    private final Supplier<MatchRegistry> matches;
    private final FaultBarrier faults;
    private final Map<UUID, List<CustomPacketPayload>> queued = new LinkedHashMap<>();
    private final Map<Integer, MatchLifecycle> lastState = new HashMap<>();
    private final NetCounters counters = new NetCounters();
    private final RateLimiter limiter = new RateLimiter();

    public NetGateway(MinecraftServer server, Supplier<MatchRegistry> matches, FaultBarrier faults) {
        this.server = server;
        this.matches = matches;
        this.faults = faults;
    }

    /** Sends {@code payload} to {@code audience}; a type that does not declare the audience is refused. */
    public void send(Audience audience, CustomPacketPayload payload) {
        check(ModPayloads.spec(payload.type().id()), payload.type().id(), audience.kind());
        switch (audience) {
            case Audience.Player p -> queue(p.player().getUUID(), payload);
            case Audience.TrackersOf t -> toTrackers(t, payload);
            case Audience.Faction f -> {
                MatchRuntime rt = matches.get().get(f.match());
                if (rt != null) {
                    rt.roster().of(f.faction()).forEach(player -> queue(player, payload));
                }
            }
            case Audience.SeesCell c -> toMembers(c.match(), payload); // every member sees every cell until fog (M8)
            case Audience.SeesEither
                    e -> // AFTER-M8: deliver to the union of the two cells' viewers once fog of war exists
                toMembers(e.match(), payload);
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
        MatchSpace space = rt.space();
        String rules = CanonicalJson.write(MatchRules.CODEC
                .encodeStart(JsonOps.INSTANCE, core.queries().config().rules())
                .getOrThrow());
        send(
                new Audience.Player(player),
                new MatchJoinPayload(new MatchJoin(
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
                        rules)));
        lastState.remove(rt.id().value());
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
        Map<Integer, MatchLifecycle> now = new HashMap<>();
        for (MatchRuntime rt : matches.get().all()) {
            now.put(rt.id().value(), rt.lifecycle());
            if (lastState.get(rt.id().value()) != rt.lifecycle() || tick % STATE_EVERY == 0) {
                send(new Audience.Match(rt.id()), state(rt));
            }
        }
        lastState.clear();
        lastState.putAll(now); // a disposed match is forgotten
        for (Map.Entry<UUID, List<CustomPacketPayload>> e : queued.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                continue; // it left this tick
            }
            List<CustomPacketPayload> sendable = new ArrayList<>();
            for (CustomPacketPayload p : e.getValue()) {
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

    /** A batch a client sent, on the server thread. */
    public void commands(ServerPlayer player, CommandsPayload payload) {
        long tick = server.getTickCount();
        CommandBatch batch = payload.batch();
        if (batch == null) {
            refuse(player, tick, "unreadable");
            return;
        }
        int handles = 0;
        for (Command command : batch.commands()) {
            handles += handles(command);
        }
        if (!limiter.take(player.getUUID(), tick, batch.commands().size(), handles)) {
            refuse(player, tick, "over rate");
            return;
        }
        MatchRegistry.Seat seat = matches.get().seatOf(player.getUUID());
        if (seat == null) {
            refuse(player, tick, "unseated");
            return;
        }
        if (seat.slot().isAdmin()) {
            // the admin's slot seats watchers, whose commands would rule every faction: dropped, not a fault
            counters.violation(player.getGameProfile().getName(), "observer");
            return;
        }
        MatchRuntime rt = seat.match();
        if (rt.lifecycle() != MatchLifecycle.RUNNING && rt.lifecycle() != MatchLifecycle.PAUSED) {
            counters.violation(player.getGameProfile().getName(), "match " + rt.lifecycle()); // dropped, not a fault
            return;
        }
        UUID id = player.getUUID();
        faults.run(rt, "commands", () -> {
            for (int k = 0; k < batch.commands().size(); k++) {
                int seq = batch.clientSeq() + k;
                rt.submit(seat.slot(), batch.commands().get(k), seq, result -> feedback(id, seq, result));
            }
        });
    }

    /** A command's result for the player who sent it, if it is still here. */
    private void feedback(UUID player, int seq, CommandResult result) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
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

    /** A player who left starts afresh. */
    public void forget(ServerPlayer player) {
        limiter.forget(player.getUUID());
        queued.remove(player.getUUID());
    }

    /** Another handler refused what {@code player} sent: a violation, counted as the gateway's own are. */
    public void refused(ServerPlayer player, String reason) {
        refuse(player, server.getTickCount(), reason);
    }

    private void refuse(ServerPlayer player, long tick, String reason) {
        counters.violation(player.getGameProfile().getName(), reason);
        if (limiter.violation(player.getUUID(), tick)) {
            limiter.forget(player.getUUID());
            player.connection.disconnect(Component.literal(TOO_MANY));
        }
    }

    private void queue(UUID player, CustomPacketPayload payload) {
        queued.computeIfAbsent(player, k -> new ArrayList<>()).add(payload);
    }

    private void toMembers(MatchId match, CustomPacketPayload payload) {
        MatchRuntime rt = matches.get().get(match);
        if (rt != null) {
            rt.roster().members().forEach(player -> queue(player, payload));
        }
    }

    private static MatchStatePayload state(MatchRuntime rt) {
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
        return new MatchStatePayload(new MatchStateMsg(rt.id().value(), lifecycle, Math.max(0, rt.tick())));
    }

    private void toTrackers(Audience.TrackersOf trackers, CustomPacketPayload payload) {
        MatchRuntime rt = matches.get().get(trackers.match());
        UnitEntity body = rt == null ? null : rt.bodies().get(trackers.handle());
        if (body != null) {
            PacketDistributor.sendToPlayersTrackingEntity(body, payload);
            counters.sent(payload.type().id(), "trackers", size(payload));
        }
    }

    private static int handles(Command command) {
        return switch (command) {
            case Move move -> move.units().size();
            case Stop stop -> stop.units().size();
            case Attack attack -> attack.units().size();
            case AttackMove move -> move.units().size();
            case Follow follow -> follow.units().size();
            default -> 0;
        };
    }

    /** A payload's record bytes, as the wire carries them. */
    static int size(CustomPacketPayload payload) {
        return switch (payload) {
            case MatchJoinPayload p -> WireCodec.encode(MatchJoin.WIRE, p.join()).length;
            case MatchStatePayload p -> WireCodec.encode(MatchStateMsg.WIRE, p.state()).length;
            case FeedbackPayload p -> WireCodec.encode(FeedbackMsg.WIRE, p.message()).length;
            case DebugFeedPayload p -> WireCodec.encode(DebugFeedMsg.WIRE, p.message()).length;
            case FxPayload p -> WireCodec.encode(Fx.WIRE, p.fx()).length;
            default -> 0;
        };
    }
}
