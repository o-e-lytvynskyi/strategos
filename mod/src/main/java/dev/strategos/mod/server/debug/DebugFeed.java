package dev.strategos.mod.server.debug;

import dev.strategos.core.protocol.DebugFeedMsg;
import dev.strategos.core.protocol.DebugLayer;
import dev.strategos.core.protocol.DebugSubscribe;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchQueries;
import dev.strategos.core.sim.nav.SurfaceGrid;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import dev.strategos.mod.server.match.MatchSpace;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.NetGateway;
import dev.strategos.mod.server.net.payload.DebugFeedPayload;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The movement overlay's feed (PATH-10, 04 §15.2): an operator who subscribes gets {@code debug_feed} for the running
 * or paused match it stands in or near, through the gateway (B6); nobody else gets any. Each layer has its rate:
 * surfaces and components at 2 Hz, a page at a time, and a page again only when it changed (its hash; for components,
 * the grid's version) or came back into the area; paths at 5 Hz, unit markers at 10 Hz, the panel at 4 Hz. A
 * subscription needs the config's {@code debugPermission} (2 by default), at most two in a second; a refused one is a
 * violation, and the player is told why. Only the server thread touches it.
 */
public final class DebugFeed {
    /** Subscriptions a player may send within {@link #RATE_WINDOW} ticks. */
    private static final int RATE = 2;

    private static final int RATE_WINDOW = 20;

    private final MinecraftServer server;
    private final Supplier<MatchRegistry> matches;
    private final NetGateway net;
    private final int permission;
    private final Map<UUID, Subscriber> subscribers = new LinkedHashMap<>();
    private final Map<UUID, ArrayDeque<Long>> recent = new HashMap<>();

    /** One operator's subscription, and what it was last sent. */
    private static final class Subscriber {
        final DebugSubscribe request;
        final long[] due = new long[DebugLayer.values().length];
        int match;
        final Map<Integer, Long> surfaces = new HashMap<>();
        final Map<Integer, Long> components = new HashMap<>();

        Subscriber(DebugSubscribe request) {
            this.request = request;
        }
    }

    public DebugFeed(MinecraftServer server, Supplier<MatchRegistry> matches, NetGateway net, int permission) {
        this.server = server;
        this.matches = matches;
        this.net = net;
        this.permission = permission;
    }

    /** The permission level the feed and its commands need. */
    public int permission() {
        return permission;
    }

    /**
     * Takes {@code player}'s subscription, which replaces any earlier one; one with no layer ends it. False when it is
     * refused: the player lacks the permission, or sent too many.
     */
    public boolean subscribe(ServerPlayer player, DebugSubscribe request) {
        String refusal = !player.hasPermissions(permission)
                ? "the debug feed needs permission level " + permission
                : !take(player.getUUID()) ? "at most " + RATE + " debug subscriptions a second" : null;
        if (refusal != null) {
            net.refused(player, "debug: " + refusal);
            player.sendSystemMessage(Component.literal("strategos: " + refusal));
            return false;
        }
        if (request.layers() == 0) {
            subscribers.remove(player.getUUID());
        } else {
            subscribers.put(player.getUUID(), new Subscriber(request));
        }
        return true;
    }

    /** {@code player}'s layers, or 0. */
    public int layers(ServerPlayer player) {
        Subscriber s = subscribers.get(player.getUUID());
        return s == null ? 0 : s.request.layers();
    }

    /** A player who left: its subscription and its rate go. */
    public void forget(ServerPlayer player) {
        subscribers.remove(player.getUUID());
        recent.remove(player.getUUID());
    }

    /**
     * Once a server tick, after the matches stepped and before the gateway flushes: each subscriber's due layers. A
     * subscriber who left or lost the permission is dropped.
     */
    public void tick() {
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, Subscriber>> it = subscribers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Subscriber> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null || !player.hasPermissions(permission)) {
                it.remove();
                continue;
            }
            feed(player, e.getValue(), now);
        }
    }

    /** Server ticks between two updates of a layer. */
    static int every(DebugLayer layer) {
        return switch (layer) {
            case SURFACES, COMPONENTS -> 10;
            case PATHS -> 4;
            case UNITS -> 2;
            case PANEL -> 5;
        };
    }

    private void feed(ServerPlayer player, Subscriber sub, long now) {
        MatchRuntime rt = near(player, sub.request.radius());
        MatchCore core = rt == null ? null : rt.core();
        if (rt == null || core == null) {
            return;
        }
        if (sub.match != rt.id().value()) {
            sub.match = rt.id().value();
            sub.surfaces.clear();
            sub.components.clear();
        }
        MatchSpace space = rt.space();
        DebugFeedMsg.Origin origin = new DebugFeedMsg.Origin(
                space.origin().getX(), space.minY(), space.origin().getZ());
        DebugFrames.Area area = DebugFrames.Area.around(
                space.localX(player.getX()),
                space.localZ(player.getZ()),
                sub.request.radius(),
                space.sizeX(),
                space.sizeZ());
        MatchQueries q = core.queries();
        for (DebugLayer layer : DebugLayer.values()) {
            if (!sub.request.wants(layer) || now < sub.due[layer.code()]) {
                continue;
            }
            sub.due[layer.code()] = now + every(layer);
            switch (layer) {
                case SURFACES -> pages(player, sub.surfaces, q.surfaces(), origin, area, layer, now);
                case COMPONENTS -> pages(player, sub.components, q.surfaces(), origin, area, layer, now);
                case PATHS -> DebugFrames.paths(q, origin, area).forEach(p -> send(player, layer, now, p));
                case UNITS -> DebugFrames.marks(q, origin, area).forEach(m -> send(player, layer, now, m));
                case PANEL -> send(player, layer, now, DebugFrames.panel(rt.id().value(), q, core.timers()));
            }
        }
    }

    /** The area's pages that changed since they were sent, or are new to it; pages that left it are forgotten. */
    private void pages(
            ServerPlayer player,
            Map<Integer, Long> sent,
            SurfaceGrid grid,
            DebugFeedMsg.Origin origin,
            DebugFrames.Area area,
            DebugLayer layer,
            long now) {
        Set<Integer> inArea = new HashSet<>();
        if (!area.isEmpty()) {
            for (int pz = area.minZ() >> 4; pz <= (area.maxZ() - 1) >> 4; pz++) {
                for (int px = area.minX() >> 4; px <= (area.maxX() - 1) >> 4; px++) {
                    int page = pz * grid.pagesX() + px;
                    inArea.add(page);
                    long key = layer == DebugLayer.SURFACES ? grid.page(px, pz).hash() : grid.version();
                    Long last = sent.put(page, key);
                    if (last == null || last != key) {
                        send(player, layer, now, DebugFrames.cells(grid, origin, px, pz, layer));
                    }
                }
            }
        }
        sent.keySet().retainAll(inArea);
    }

    private void send(ServerPlayer player, DebugLayer layer, long now, DebugFeedMsg.Payload payload) {
        net.send(new Audience.Player(player), new DebugFeedPayload(new DebugFeedMsg(layer, now, payload)));
    }

    /**
     * The running or paused match of the player's level nearest it, at most {@code radius} blocks beyond its edge; the
     * lowest id on a tie.
     */
    private @Nullable MatchRuntime near(ServerPlayer player, int radius) {
        MatchRuntime best = null;
        double bestDistance = Double.MAX_VALUE;
        for (MatchRuntime rt : matches.get().activeIn(player.serverLevel())) {
            MatchSpace s = rt.space();
            double dx = Math.max(
                    s.origin().getX() - player.getX(),
                    player.getX() - (s.origin().getX() + s.sizeX()));
            double dz = Math.max(
                    s.origin().getZ() - player.getZ(),
                    player.getZ() - (s.origin().getZ() + s.sizeZ()));
            double distance = Math.max(0, Math.max(dx, dz));
            if (distance <= radius && distance < bestDistance) {
                best = rt;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Whether {@code player} may subscribe now; if so, the subscription is counted. */
    private boolean take(UUID player) {
        long now = server.getTickCount();
        ArrayDeque<Long> ticks = recent.computeIfAbsent(player, k -> new ArrayDeque<>());
        while (!ticks.isEmpty() && ticks.peekFirst() <= now - RATE_WINDOW) {
            ticks.pollFirst();
        }
        if (ticks.size() >= RATE) {
            return false;
        }
        ticks.addLast(now);
        return true;
    }
}
