package dev.strategos.core.client.hud;

import dev.strategos.core.protocol.BuildingState;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Which minimap tiles (one per 16 × 16 chunk) to rebuild ahead of the round (UI-20): a tile is dirty when a building
 * on or beside it changes state or progress, and a dirty tile is rebuilt in the first tick it is due, which is at once
 * unless it was built within the last {@value #EVERY} ticks (a second): within a second of the change, and at most once
 * a second. Times are client ticks.
 */
public final class DirtyTiles {
    /** The least ticks between two builds of one tile. */
    public static final int EVERY = 20;

    private final int chunksX;
    private final int chunksZ;
    private final long[] builtAt;
    private final LinkedHashSet<Integer> dirty = new LinkedHashSet<>();
    private Map<Integer, Seen> seen = new HashMap<>();

    /** A building as last observed: its state and progress packed, and its corner. */
    private record Seen(long key, int x, int z) {}

    public DirtyTiles(int chunksX, int chunksZ) {
        this.chunksX = chunksX;
        this.chunksZ = chunksZ;
        builtAt = new long[chunksX * chunksZ];
        Arrays.fill(builtAt, -1_000_000L);
    }

    /** Marks tile {@code tile} dirty; one outside the space is ignored. */
    public void mark(int tile) {
        if (tile >= 0 && tile < builtAt.length) {
            dirty.add(tile);
        }
    }

    /** Marks the tile of chunk ({@code cx}, {@code cz}) and the eight round it, a footprint reaching over a corner. */
    public void markAround(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                if (x >= 0 && z >= 0 && x < chunksX && z < chunksZ) {
                    dirty.add(z * chunksX + x);
                }
            }
        }
    }

    /**
     * Marks the tiles round every building that appeared, changed state or progress, or went away since the last
     * call.
     */
    public void observe(List<BuildingState.Entry> buildings) {
        Map<Integer, Seen> now = new HashMap<>();
        for (BuildingState.Entry b : buildings) {
            Seen at = new Seen(((long) b.state() << 32) | (b.progress() & 0xFFFFFFFFL), b.x(), b.z());
            now.put(b.handle(), at);
            Seen before = seen.remove(b.handle());
            if (before == null || before.key() != at.key()) {
                markAround(b.x() >> 4, b.z() >> 4);
            }
        }
        for (Seen gone : seen.values()) { // destroyed or demolished: its ground changed too
            markAround(gone.x() >> 4, gone.z() >> 4);
        }
        seen = now;
    }

    /** Records that tile {@code tile} was built at {@code now} (the round builds tiles too). */
    public void built(int tile, long now) {
        builtAt[tile] = now;
    }

    /**
     * Rebuilds up to {@code budget} dirty tiles that are due at {@code now}, oldest mark first: each is passed to
     * {@code rebuild} and stamped built. A tile built within {@value #EVERY} ticks waits its turn, still dirty.
     */
    public void drain(long now, int budget, IntConsumer rebuild) {
        int left = budget;
        for (var it = dirty.iterator(); it.hasNext() && left > 0; ) {
            int tile = it.next();
            if (now - builtAt[tile] < EVERY) {
                continue;
            }
            it.remove();
            builtAt[tile] = now;
            rebuild.accept(tile);
            left--;
        }
    }

    /** How many tiles wait. */
    public int pending() {
        return dirty.size();
    }
}
