package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.TerritoryUpdate;
import dev.strategos.core.shared.TerritoryGrid;
import dev.strategos.core.sim.territory.TerritoryUpdates;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * What each player was sent of the territory (TERR-01, M9 spec §2.4): a copy of the last grid queued for it and the
 * parts of its next version, handed out one per call to {@link #poll}. A new player gets a full version, the others
 * the changed tiles; a version that moves while parts are still queued waits for the queue to drain. No Minecraft.
 * Server thread only.
 */
public final class TerritoryFeed {
    private static final class Sent {
        final int matchId;

        @Nullable TerritoryGrid last;

        long version = -1;
        final ArrayDeque<TerritoryUpdate> queue = new ArrayDeque<>();

        Sent(int matchId) {
            this.matchId = matchId;
        }
    }

    private final Map<UUID, Sent> sent = new HashMap<>();

    /**
     * Queues the parts of {@code version} for {@code player} when it is not the one it has, and none is still queued.
     * The grid is copied only then.
     */
    public void offer(UUID player, int matchId, long tick, long version, TerritoryGrid grid) {
        Sent s = sent.get(player);
        if (s == null || s.matchId != matchId) {
            s = new Sent(matchId);
            sent.put(player, s);
        }
        if (s.version == version || !s.queue.isEmpty()) {
            return;
        }
        s.queue.addAll(TerritoryUpdates.encode(matchId, tick, (int) version, s.last, grid));
        TerritoryGrid last = s.last;
        if (last == null || last.tilesX() != grid.tilesX() || last.tilesZ() != grid.tilesZ()) {
            last = new TerritoryGrid(grid.tilesX(), grid.tilesZ());
            s.last = last;
        }
        last.copyFrom(grid);
        s.version = version;
    }

    /** The next part for {@code player}, or null when none is queued. */
    public @Nullable TerritoryUpdate poll(UUID player) {
        Sent s = sent.get(player);
        return s == null ? null : s.queue.poll();
    }

    public void forget(UUID player) {
        sent.remove(player);
    }

    /** Forgets every player {@code keep} rejects: those that left, or whose match ended. */
    public void retain(Predicate<UUID> keep) {
        sent.keySet().removeIf(p -> !keep.test(p));
    }

    public int players() {
        return sent.size();
    }
}
