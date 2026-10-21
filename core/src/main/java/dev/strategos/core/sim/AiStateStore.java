package dev.strategos.core.sim;

import java.util.Arrays;

/** Each unit's top-level state, by handle index, stored as {@link UnitState#code()}. */
public final class AiStateStore {
    private byte[] state = new byte[0];

    public void ensureCapacity(int size) {
        if (size > state.length) {
            state = Arrays.copyOf(state, Math.max(size, state.length * 2));
        }
    }

    public UnitState state(int i) {
        return UnitState.ofCode(state[i]);
    }

    public void set(int i, UnitState s) {
        state[i] = (byte) s.code();
    }
}
