package dev.strategos.core.sim;

import java.util.Arrays;

/** Each unit's current order, by handle index: {@link #NONE} or {@link #MOVE} to a target on the ground plane. */
public final class OrderStore {
    public static final int NONE = 0;
    public static final int MOVE = 1;

    private int[] kind = new int[0];
    private double[] targetX = new double[0];
    private double[] targetZ = new double[0];

    public void ensureCapacity(int size) {
        if (size > kind.length) {
            int capacity = Math.max(size, kind.length * 2);
            kind = Arrays.copyOf(kind, capacity);
            targetX = Arrays.copyOf(targetX, capacity);
            targetZ = Arrays.copyOf(targetZ, capacity);
        }
    }

    public int kind(int i) {
        return kind[i];
    }

    public double targetX(int i) {
        return targetX[i];
    }

    public double targetZ(int i) {
        return targetZ[i];
    }

    public void setMove(int i, double x, double z) {
        kind[i] = MOVE;
        targetX[i] = x;
        targetZ[i] = z;
    }

    public void clear(int i) {
        kind[i] = NONE;
        targetX[i] = 0;
        targetZ[i] = 0;
    }
}
