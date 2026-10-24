package dev.strategos.core.sim;

import java.util.Arrays;

/**
 * This tick's commanders who stand in first person (CAM-07), per player slot their x, z and yaw from {@link
 * LeaderUpdate} inputs; cleared as each tick starts. Split out of {@link MatchState} in M4 T1.
 */
public final class Leaders {
    private final float[] at = new float[LeaderUpdate.SLOTS * 3]; // per slot x, z, yaw
    private final boolean[] leading = new boolean[LeaderUpdate.SLOTS];

    void clear() {
        Arrays.fill(leading, false);
    }

    void set(LeaderUpdate leader) {
        int k = leader.slot() * 3;
        at[k] = leader.x();
        at[k + 1] = leader.z();
        at[k + 2] = leader.yaw();
        leading[leader.slot()] = true;
    }

    /** Whether the commander of player slot {@code slot} stands in first person this tick. */
    public boolean leads(int slot) {
        return slot >= 0 && slot < LeaderUpdate.SLOTS && leading[slot];
    }

    /** This tick's X of the commander of {@code slot}, when it {@link #leads}. */
    public double x(int slot) {
        return at[slot * 3];
    }

    public double z(int slot) {
        return at[slot * 3 + 1];
    }

    public double yaw(int slot) {
        return at[slot * 3 + 2];
    }
}
