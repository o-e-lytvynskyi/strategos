package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.Command;
import dev.strategos.core.protocol.CommandBatch;
import dev.strategos.core.protocol.FeedbackMsg;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.core.protocol.Move;
import dev.strategos.core.protocol.Stop;
import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.shared.MatchId;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.registry.ModPayloads;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.net.payload.CommandsPayload;
import dev.strategos.mod.server.net.payload.FeedbackPayload;
import dev.strategos.mod.server.net.payload.MatchJoinPayload;
import dev.strategos.mod.server.net.payload.MatchStatePayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The only sender (ADR-0016, rule B6). A payload goes to an audience its type declares; to players it is queued and
 * flushed once a tick ({@code ServerTickEvent.Post}), all of a player's in one bundle, and a player whose client lacks
 * our channel, such as a vanilla client, gets none; to a body's trackers it goes at once, through vanilla's tracking.
 * Every payload's record bytes are counted per type and player. It also reads the {@code commands} clients send:
 * refused batches and senders with no seat are violations, and the 101st in a minute disconnects (spec §6.5).
 */
public final class NetGateway {
    /** The reason a player hears when it is disconnected for its violations. */
    public static final String TOO_MANY = "strategos: too many invalid packets";

    private final MinecraftServer server;
    private final Supplier<MatchRegistry> matches;
    private final Map<UUID, List<CustomPacketPayload>> queued = new LinkedHashMap<>();
    private final NetCounters counters = new NetCounters();
    private final RateLimiter limiter = new RateLimiter();

    public NetGateway(MinecraftServer server, Supplier<MatchRegistry> matches) {
        this.server = server;
        this.matches = matches;
    }

    /** Sends {@code payload} to {@code audience}; a type that does not declare the audience is refused. */
    public void send(Audience audience, CustomPacketPayload payload) {
        check(ModPayloads.spec(payload.type().id()), payload.type().id(), audience.kind());
        switch (audience) {
            case Audience.Player p -> queue(p.player(), payload);
            case Audience.TrackersOf t -> toTrackers(t, payload);
            case Audience.Faction f -> members(f.match());
            case Audience.SeesCell c -> members(c.match());
            case Audience.Match m -> members(m.match());
        }
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

    /** Sends every queued payload, a player's together; once a tick, after the matches have stepped. */
    public void flush() {
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
        refuse(player, tick, "unseated"); // T15.2's roster seats players; until then no one is
    }

    public NetCounters counters() {
        return counters;
    }

    /** A player who left starts afresh. */
    public void forget(ServerPlayer player) {
        limiter.forget(player.getUUID());
        queued.remove(player.getUUID());
    }

    private void refuse(ServerPlayer player, long tick, String reason) {
        counters.violation(player.getGameProfile().getName(), reason);
        if (limiter.violation(player.getUUID(), tick)) {
            limiter.forget(player.getUUID());
            player.connection.disconnect(Component.literal(TOO_MANY));
        }
    }

    private void queue(ServerPlayer player, CustomPacketPayload payload) {
        queued.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(payload);
    }

    private void toTrackers(Audience.TrackersOf trackers, CustomPacketPayload payload) {
        MatchRuntime rt = matches.get().get(trackers.match());
        UnitEntity body = rt == null ? null : rt.bodies().get(trackers.handle());
        if (body != null) {
            PacketDistributor.sendToPlayersTrackingEntity(body, payload);
            counters.sent(payload.type().id(), "trackers", size(payload));
        }
    }

    /** A match's members, the players T15.2's roster seats; none yet. */
    @SuppressWarnings("UnusedVariable") // the match names the roster to read once it exists
    private void members(MatchId match) {}

    private static int handles(Command command) {
        return switch (command) {
            case Move move -> move.units().size();
            case Stop stop -> stop.units().size();
            default -> 0;
        };
    }

    /** A payload's record bytes, as the wire carries them. */
    static int size(CustomPacketPayload payload) {
        return switch (payload) {
            case MatchJoinPayload p -> WireCodec.encode(MatchJoin.WIRE, p.join()).length;
            case MatchStatePayload p -> WireCodec.encode(MatchStateMsg.WIRE, p.state()).length;
            case FeedbackPayload p -> WireCodec.encode(FeedbackMsg.WIRE, p.message()).length;
            default -> 0;
        };
    }
}
