package dev.strategos.core.sim.vision;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Hasher;
import dev.strategos.core.sim.EntityStore;
import dev.strategos.core.sim.IdentityStore;
import dev.strategos.core.sim.MotionStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Which units one faction sees: its own, and an enemy's from the tick it stands in a seen cell until
 * {@link #HYSTERESIS} ticks after it last did. Keyed by unit index.
 */
@CoreApi
public final class VisibilityIndex {
    static final int HYSTERESIS = 10;
    private static final long NONE = -1;

    private final BitSet visible = new BitSet();
    private long[] lastSeen = new long[0];
    private int version;

    public boolean isVisible(int handle) {
        return visible.get(Handle.index(handle));
    }

    /** Counts the updates that changed what the faction sees; equal values mean an equal set. */
    public int version() {
        return version;
    }

    public void update(
            int thisFaction,
            long tick,
            EntityStore entities,
            IdentityStore identity,
            MotionStore motion,
            VisionGrid grid) {
        int high = entities.highWater();
        if (lastSeen.length < high) {
            int old = lastSeen.length;
            lastSeen = Arrays.copyOf(lastSeen, Math.max(high, old * 2));
            Arrays.fill(lastSeen, old, lastSeen.length, NONE);
        }
        boolean changed = false;
        for (int i = 0; i < high; i++) {
            boolean held = false;
            if (entities.aliveAt(i)) {
                if (identity.faction(i) == thisFaction) {
                    if (!visible.get(i)) {
                        changed = true;
                    }
                    visible.set(i);
                    lastSeen[i] = NONE;
                    continue;
                }
                if (grid.count(VisionGrid.cellOf(motion.x(i)), VisionGrid.cellOf(motion.z(i))) > 0) {
                    if (!visible.get(i)) {
                        changed = true;
                    }
                    visible.set(i);
                    lastSeen[i] = tick;
                    continue;
                }
                held = visible.get(i) && lastSeen[i] != NONE && tick - lastSeen[i] <= HYSTERESIS;
            }
            if (!held) {
                if (visible.get(i)) {
                    changed = true;
                }
                visible.clear(i);
                lastSeen[i] = NONE;
            }
        }
        if (changed) {
            version++;
        }
    }

    boolean pristine() {
        for (long t : lastSeen) {
            if (t != NONE) {
                return false;
            }
        }
        return true;
    }

    /** Pairs of unit index and the tick it was last seen, for each enemy unit held visible. */
    public List<Long> saveHysteresis() {
        List<Long> out = new ArrayList<>();
        for (int i = 0; i < lastSeen.length; i++) {
            if (lastSeen[i] != NONE) {
                out.add((long) i);
                out.add(lastSeen[i]);
            }
        }
        return out;
    }

    public void restoreHysteresis(List<Long> data) {
        if (data.size() % 2 != 0) {
            throw new IllegalArgumentException("a saved hysteresis of an odd length " + data.size());
        }
        visible.clear();
        lastSeen = new long[0];
        for (int k = 0; k < data.size(); k += 2) {
            int i = Math.toIntExact(data.get(k));
            if (i >= lastSeen.length) {
                int old = lastSeen.length;
                lastSeen = Arrays.copyOf(lastSeen, i + 1);
                Arrays.fill(lastSeen, old, lastSeen.length, NONE);
            }
            lastSeen[i] = data.get(k + 1);
            visible.set(i);
        }
    }

    public long mixHysteresis(long h) {
        for (int i = 0; i < lastSeen.length; i++) {
            if (lastSeen[i] != NONE) {
                h = Hasher.mix(h, i);
                h = Hasher.mix(h, lastSeen[i]);
            }
        }
        return h;
    }
}
