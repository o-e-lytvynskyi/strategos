package dev.strategos.mod.server.fog;

import dev.strategos.core.shared.MatchId;
import dev.strategos.core.sim.MatchCore;
import dev.strategos.core.sim.MatchLifecycle;
import dev.strategos.core.sim.vision.VisionSystem;
import dev.strategos.mod.server.commander.CommanderGuard;
import dev.strategos.mod.server.match.MatchRegistry;
import dev.strategos.mod.server.match.MatchRuntime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Makes vanilla's tracking ask the fog gate again (FOW-07): every {@value #EVERY} ticks, at offset {@value #OFFSET},
 * each seated player whose faction's visibility changed since the last refresh gets {@code ChunkMap#move} through
 * {@code ServerChunkCache#move}, which re-runs {@code broadcastToPlayer} for the entities around the player.
 */
public final class TrackerRefresher {
    static final int EVERY = 5;
    static final int OFFSET = 1;

    private final MinecraftServer server;
    private final Supplier<MatchRegistry> matches;
    private final Map<Long, Integer> seen = new HashMap<>();
    private final Map<UUID, PlayerView> playerViews = new HashMap<>();
    private long nanos;

    private record PlayerView(int match, long generation, int primary, Connection connection, Set<UUID> denied) {}

    public TrackerRefresher(MinecraftServer server, Supplier<MatchRegistry> matches) {
        this.server = server;
        this.matches = matches;
    }

    static boolean due(int tick) {
        return tick % EVERY == OFFSET;
    }

    /** The time the refreshes have taken since the server began, for the perf soaks (PERF-09). */
    public long nanos() {
        return nanos;
    }

    public void tick() {
        int now = server.getTickCount();
        if (!due(now)) {
            return;
        }
        long start = System.nanoTime();
        try {
            refresh();
        } finally {
            nanos += System.nanoTime() - start;
        }
    }

    private void refresh() {
        MatchRegistry registry = matches.get();
        Set<UUID> refresh = new HashSet<>();
        seen.keySet().removeIf(key -> registry.get(new MatchId((int) (key >> 8))) == null);
        for (MatchRuntime rt : registry.all()) {
            MatchCore core = rt.core();
            if (core == null || core.vision() == null || rt.lifecycle() != MatchLifecycle.RUNNING) {
                continue;
            }
            VisionSystem vision = core.vision();
            int factions = core.queries().config().factions();
            boolean[] changed = new boolean[factions];
            for (int f = 0; f < factions; f++) {
                long key = ((long) rt.id().value() << 8) | f;
                int version = vision.visibility(f).version();
                Integer last = seen.put(key, version);
                changed[f] = last == null || last != version;
            }
            for (UUID member : rt.roster().members()) {
                var seat = registry.seatOf(member);
                int primary = seat == null
                        ? -1
                        : seat.primaryFaction().map(f -> f.value()).orElse(-1);
                if (primary < 0 || primary >= factions || !changed[primary]) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(member);
                if (player != null) {
                    refresh.add(member);
                }
            }
        }
        // A player can leave a visible cell without changing any core-unit visibility bit.
        // Compare denied subjects, so enrolling a previously public body also revokes its old pairing.
        Set<UUID> online = new HashSet<>();
        var players = server.getPlayerList().getPlayers();
        for (ServerPlayer viewer : players) {
            UUID id = viewer.getUUID();
            online.add(id);
            Set<UUID> denied = new HashSet<>();
            for (ServerPlayer subject : players) {
                if (!subject.getUUID().equals(id) && !CommanderGuard.allowsBody(subject, viewer)) {
                    denied.add(subject.getUUID());
                }
            }
            var seat = registry.seatOf(id);
            var view = new PlayerView(
                    seat == null ? 0 : seat.match().id().value(),
                    seat == null ? 0 : seat.match().roster().generationOf(id),
                    seat == null
                            ? -1
                            : seat.primaryFaction().map(f -> f.value()).orElse(-1),
                    viewer.connection.getConnection(),
                    Set.copyOf(denied));
            if (!view.equals(playerViews.put(id, view))) refresh.add(id);
        }
        playerViews.keySet().retainAll(online);
        for (UUID id : refresh) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(id);
            if (viewer != null) viewer.serverLevel().getChunkSource().move(viewer);
        }
    }
}
