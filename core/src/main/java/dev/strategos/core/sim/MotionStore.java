package dev.strategos.core.sim;

import java.util.Arrays;

/** Each unit's pose: world X, Y, Z in blocks and yaw in degrees, by handle index. */
public final class MotionStore {
    private double[] x = new double[0];
    private double[] y = new double[0];
    private double[] z = new double[0];
    private float[] yaw = new float[0];

    public void ensureCapacity(int size) {
        if (size > x.length) {
            int capacity = Math.max(size, x.length * 2);
            x = Arrays.copyOf(x, capacity);
            y = Arrays.copyOf(y, capacity);
            z = Arrays.copyOf(z, capacity);
            yaw = Arrays.copyOf(yaw, capacity);
        }
    }

    public double x(int i) {
        return x[i];
    }

    public double y(int i) {
        return y[i];
    }

    public double z(int i) {
        return z[i];
    }

    public float yaw(int i) {
        return yaw[i];
    }

    public void set(int i, double x, double y, double z, float yaw) {
        this.x[i] = x;
        this.y[i] = y;
        this.z[i] = z;
        this.yaw[i] = yaw;
    }
}
