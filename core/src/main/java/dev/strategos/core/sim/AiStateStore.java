package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.unitai.StateId;
import java.util.Arrays;

/**
 * Each unit's state machine (UNIT-02, spec §5.9), by handle index: the leaf of the UnitAI tree it is in, by code; the
 * top-level state that leaf reports, which views, events and the movement systems read; and the two data words the
 * leaf keeps. A unit whose words are both 0 hashes and saves as in M0.
 */
public final class AiStateStore {
    private static final byte GARRISONED = (byte) UnitState.GARRISONED.code();

    private short[] state = new short[0];
    private byte[] activity = new byte[0];
    private int[] data0 = new int[0];
    private int[] data1 = new int[0];

    /** One unit's machine as a snapshot keeps it: its leaf and the two data words, both 0 in every M0 save. */
    @CoreApi
    public record Saved(StateId state, int data0, int data1) {
        /** A unit in {@code state} with both data words 0. */
        public static Saved of(StateId state) {
            return new Saved(state, 0, 0);
        }
    }

    public void ensureCapacity(int size) {
        if (size > state.length) {
            int capacity = Math.max(size, state.length * 2);
            state = Arrays.copyOf(state, capacity);
            activity = Arrays.copyOf(activity, capacity);
            data0 = Arrays.copyOf(data0, capacity);
            data1 = Arrays.copyOf(data1, capacity);
        }
    }

    /** The unit's top-level state: the activity its leaf reports. */
    public UnitState state(int i) {
        return UnitState.ofCode(activity[i]);
    }

    /** Whether the unit is inside a building (BLD-16). */
    public boolean garrisoned(int i) {
        return activity[i] == GARRISONED;
    }

    /** The code of the leaf the unit is in. */
    public int code(int i) {
        return state[i];
    }

    public int data0(int i) {
        return data0[i];
    }

    public int data1(int i) {
        return data1[i];
    }

    /** Whether both data words are 0, as in every M0 match: such a unit hashes and saves no words. */
    public boolean plain(int i) {
        return data0[i] == 0 && data1[i] == 0;
    }

    /** Puts the unit in {@code leaf}, keeping its data words; a state that reports no activity is no leaf. */
    public void set(int i, StateId leaf) {
        UnitState reported = leaf.activity();
        if (reported == null) {
            throw new IllegalArgumentException("state " + leaf + " reports no activity, so it is no leaf");
        }
        state[i] = (short) leaf.code();
        activity[i] = (byte) reported.code();
    }

    public void setData(int i, int data0, int data1) {
        this.data0[i] = data0;
        this.data1[i] = data1;
    }

    /** A new unit in {@code leaf}, its words 0: units enter their machine in its initial leaf, with no hooks. */
    public void reset(int i, StateId leaf) {
        set(i, leaf);
        setData(i, 0, 0);
    }

    /** A restored unit, as saved. */
    public void restore(int i, Saved saved) {
        set(i, saved.state());
        setData(i, saved.data0(), saved.data1());
    }
}
