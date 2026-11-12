package dev.strategos.core.sim.order;

import dev.strategos.core.shared.Hasher;
import dev.strategos.core.shared.Stance;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * What a unit's stance (CMB-05) needs beyond its template: the stance a player set, and the spot a defensive unit
 * returns to. A row belongs to the unit handle it was written for, so the row of a unit that died is never read for
 * the next unit of its slot. Nothing is kept for a unit the player never set a stance for, unless it is defensive, so
 * a match that never uses stances saves and hashes as before.
 */
public final class StanceStore {
    private static final int NONE = -1;

    private final IntUnaryOperator liveHandle;
    private int[] handles = new int[0];
    private byte[] stances = new byte[0];
    private double[] spotX = new double[0];
    private double[] spotZ = new double[0];
    private int top = -1;

    /** A store for the units of an entity table; {@code liveHandle} maps an index to its live handle or none. */
    public StanceStore(IntUnaryOperator liveHandle) {
        this.liveHandle = liveHandle;
    }

    /** One saved row: the unit, its stance code or -1 for its template's, and its spot. */
    public record Saved(int handle, int stance, double x, double z) {
        public Saved {
            if (stance < NONE || (stance >= 0 && Stance.of(stance) == null)) {
                throw new IllegalArgumentException("no stance has code " + stance);
            }
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("invalid stance spot");
            }
        }
    }

    private boolean live(int index) {
        return index <= top && handles[index] != 0 && handles[index] == liveHandle.applyAsInt(index);
    }

    /** Whether no live unit has a row. */
    public boolean empty() {
        for (int i = 0; i <= top; i++) {
            if (live(i)) {
                return false;
            }
        }
        return true;
    }

    /** The stance a player set for unit {@code index}, or null when it fights as its template says. */
    public @Nullable Stance explicit(int index) {
        return live(index) && stances[index] != NONE ? Stance.of(stances[index]) : null;
    }

    /** Whether unit {@code index} has a spot. */
    public boolean hasSpot(int index) {
        return live(index);
    }

    public double spotX(int index) {
        return spotX[index];
    }

    public double spotZ(int index) {
        return spotZ[index];
    }

    /** Unit {@code index}'s stance as the player set it, or the template's when {@code stance} is null, and its spot. */
    public void set(int index, @Nullable Stance stance, double x, double z) {
        int handle = liveHandle.applyAsInt(index);
        if (handle == 0) {
            throw new IllegalArgumentException("no live unit at " + index);
        }
        if (index >= handles.length) {
            int size = Math.max(index + 1, handles.length * 2);
            handles = Arrays.copyOf(handles, size);
            stances = Arrays.copyOf(stances, size);
            spotX = Arrays.copyOf(spotX, size);
            spotZ = Arrays.copyOf(spotZ, size);
        }
        if (!live(index)) {
            stances[index] = NONE;
        }
        handles[index] = handle;
        if (stance != null) {
            stances[index] = (byte) stance.code();
        }
        spotX[index] = x;
        spotZ[index] = z;
        top = Math.max(top, index);
    }

    /** Forgets unit {@code index}'s row, as the unit dies or its slot is taken again. */
    public void clear(int index) {
        if (index <= top) {
            handles[index] = 0;
            stances[index] = NONE;
        }
    }

    /** The live rows, in index order. */
    public List<Saved> save() {
        List<Saved> rows = new ArrayList<>();
        for (int i = 0; i <= top; i++) {
            if (live(i)) {
                rows.add(new Saved(handles[i], stances[i], spotX[i], spotZ[i]));
            }
        }
        return rows;
    }

    /** Rows saved by {@link #save}; the units must be alive. */
    public void restore(List<Saved> rows, IntUnaryOperator indexOf) {
        for (Saved row : rows) {
            int index = indexOf.applyAsInt(row.handle());
            if (liveHandle.applyAsInt(index) != row.handle()) {
                throw new IllegalArgumentException("a saved stance for a unit that is not alive");
            }
            set(index, row.stance() == NONE ? null : Stance.of(row.stance()), row.x(), row.z());
        }
    }

    /** Folds the live rows into hash {@code h}. */
    public long mix(long h) {
        h = Hasher.mix(h, 0x5354414e4345L);
        for (int i = 0; i <= top; i++) {
            if (live(i)) {
                h = Hasher.mix(h, i);
                h = Hasher.mix(h, stances[i]);
                h = Hasher.mix(h, Double.doubleToLongBits(spotX[i]));
                h = Hasher.mix(h, Double.doubleToLongBits(spotZ[i]));
            }
        }
        return h;
    }
}
