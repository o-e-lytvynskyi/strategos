package dev.strategos.core.sim.territory;

import dev.strategos.core.protocol.TerritoryUpdate;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TerritoryGrid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Encodes a territory version for the clients (M9 spec §2.4): the whole grid as runs when there is no previous one,
 * else the changed tiles; a part is closed once it reaches {@link TerritoryUpdate#SPLIT_AT} bytes, so every part stays
 * under {@link TerritoryUpdate#CAP}. An unchanged grid encodes to nothing.
 */
@CoreApi
public final class TerritoryUpdates {
    private static final int HEADER = 40; // the header's worst case

    private TerritoryUpdates() {}

    public static List<TerritoryUpdate> encode(
            int matchId, long tick, int version, @Nullable TerritoryGrid previous, TerritoryGrid next) {
        boolean full = previous == null || previous.tilesX() != next.tilesX() || previous.tilesZ() != next.tilesZ();
        Parts parts = new Parts(matchId, tick, version, next, full);
        if (full) {
            int i = 0;
            while (i < next.size()) {
                byte v = next.value(i);
                int run = 1;
                while (i + run < next.size() && next.value(i + run) == v) {
                    run++;
                }
                parts.add(i, run, v, varIntSize(run) + 1);
                i += run;
            }
        } else {
            TerritoryGrid before = java.util.Objects.requireNonNull(previous);
            int prev = -1;
            for (int i = 0; i < next.size(); i++) {
                if (next.value(i) != before.value(i)) {
                    parts.add(i, i, next.value(i), varIntSize(i - prev - 1) + 1);
                    prev = i;
                }
            }
        }
        return parts.finish();
    }

    static int varIntSize(int v) {
        int n = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            n++;
        }
        return n;
    }

    private static final class Parts {
        final int matchId;
        final long tick;
        final int version;
        final TerritoryGrid grid;
        final boolean full;
        final List<TerritoryUpdate> out = new ArrayList<>();
        int[] keys = new int[64];
        byte[] values = new byte[64];
        int n;
        int start = -1;
        int bytes = HEADER;

        Parts(int matchId, long tick, int version, TerritoryGrid grid, boolean full) {
            this.matchId = matchId;
            this.tick = tick;
            this.version = version;
            this.grid = grid;
            this.full = full;
        }

        void add(int index, int key, byte value, int size) {
            if (n > 0 && bytes + size > TerritoryUpdate.SPLIT_AT) {
                flush(false);
            }
            if (n == 0) {
                start = index;
            }
            if (n == keys.length) {
                keys = Arrays.copyOf(keys, n * 2);
                values = Arrays.copyOf(values, n * 2);
            }
            keys[n] = key;
            values[n] = value;
            n++;
            bytes += size;
        }

        void flush(boolean last) {
            out.add(new TerritoryUpdate(
                    matchId,
                    tick,
                    version,
                    grid.tilesX(),
                    grid.tilesZ(),
                    full,
                    out.size(),
                    last,
                    Math.max(start, 0),
                    Arrays.copyOf(keys, n),
                    Arrays.copyOf(values, n)));
            n = 0;
            start = -1;
            bytes = HEADER;
        }

        List<TerritoryUpdate> finish() {
            if (n > 0 || full) {
                flush(true);
            }
            return List.copyOf(out);
        }
    }
}
