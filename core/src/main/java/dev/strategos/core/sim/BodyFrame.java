package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.HotPath;
import java.util.Arrays;

/** Packed poses of the bodies that moved in a tick, in handle-index order. Immutable. */
@CoreApi
@HotPath
public final class BodyFrame {
    public static final BodyFrame EMPTY =
            new BodyFrame(0, new int[0], new double[0], new double[0], new double[0], new float[0], new byte[0]);

    private final int count;
    private final int[] handles;
    private final double[] x;
    private final double[] y;
    private final double[] z;
    private final float[] yaw;
    private final byte[] pose;

    private BodyFrame(int count, int[] handles, double[] x, double[] y, double[] z, float[] yaw, byte[] pose) {
        this.count = count;
        this.handles = handles;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pose = pose;
    }

    public int count() {
        return count;
    }

    public int handle(int i) {
        return handles[check(i)];
    }

    public double x(int i) {
        return x[check(i)];
    }

    public double y(int i) {
        return y[check(i)];
    }

    public double z(int i) {
        return z[check(i)];
    }

    public float yaw(int i) {
        return yaw[check(i)];
    }

    /** 0 is standing, M0's only pose. */
    public byte pose(int i) {
        return pose[check(i)];
    }

    public static Builder builder(int expected) {
        return new Builder(expected);
    }

    private int check(int i) {
        if (i < 0 || i >= count) {
            throw new IndexOutOfBoundsException("body " + i + " of " + count);
        }
        return i;
    }

    /** Collects one tick's poses; {@link #build()} freezes them. */
    @CoreApi
    public static final class Builder {
        private int count;
        private int[] handles;
        private double[] x;
        private double[] y;
        private double[] z;
        private float[] yaw;
        private byte[] pose;

        private Builder(int expected) {
            int capacity = Math.max(expected, 4);
            handles = new int[capacity];
            x = new double[capacity];
            y = new double[capacity];
            z = new double[capacity];
            yaw = new float[capacity];
            pose = new byte[capacity];
        }

        public void add(int handle, double x, double y, double z, float yaw, byte pose) {
            if (count == handles.length) {
                int capacity = handles.length * 2;
                handles = Arrays.copyOf(handles, capacity);
                this.x = Arrays.copyOf(this.x, capacity);
                this.y = Arrays.copyOf(this.y, capacity);
                this.z = Arrays.copyOf(this.z, capacity);
                this.yaw = Arrays.copyOf(this.yaw, capacity);
                this.pose = Arrays.copyOf(this.pose, capacity);
            }
            handles[count] = handle;
            this.x[count] = x;
            this.y[count] = y;
            this.z[count] = z;
            this.yaw[count] = yaw;
            this.pose[count] = pose;
            count++;
        }

        /** Empties the builder, keeping its arrays: for a benchmark that runs a system again and again. */
        public void reset() {
            count = 0;
        }

        public BodyFrame build() {
            if (count == 0) {
                return EMPTY;
            }
            return new BodyFrame(
                    count,
                    Arrays.copyOf(handles, count),
                    Arrays.copyOf(x, count),
                    Arrays.copyOf(y, count),
                    Arrays.copyOf(z, count),
                    Arrays.copyOf(yaw, count),
                    Arrays.copyOf(pose, count));
        }
    }
}
