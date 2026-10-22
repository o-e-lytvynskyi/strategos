package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import java.util.Arrays;

/**
 * Each unit's pose, by handle index: world X, Y, Z in blocks and yaw in degrees. On terrain (PATH-05, spec §5.8) also
 * its velocity in blocks per tick, the floor of the surface it stands on in 1/16 above the band's bottom ({@link
 * #NO_FLOOR} while it stands on none; a floor, unlike a layer index, survives the page's rebuild), whether it is
 * halfway up a step, the stuck monitor's target, best distance to it and ticks without progress, and the point its
 * arrival latch holds it to. A unit of a match without terrain keeps only its pose, as in M0.
 */
public final class MotionStore {
    /** No surface known: the unit has not touched terrain yet, or stands on none. */
    public static final int NO_FLOOR = -1;

    private double[] x = new double[0];
    private double[] y = new double[0];
    private double[] z = new double[0];
    private float[] yaw = new float[0];
    private double[] vx = new double[0];
    private double[] vz = new double[0];
    private int[] floor = new int[0];
    private boolean[] rising = new boolean[0];
    private double[] aimX = new double[0];
    private double[] aimZ = new double[0];
    private double[] best = new double[0];
    private int[] stuck = new int[0];
    private boolean[] latched = new boolean[0];
    private double[] latchX = new double[0];
    private double[] latchZ = new double[0];
    private int[] pushed = new int[0];

    /** The terrain part of a unit's motion, as a snapshot keeps it. */
    @CoreApi
    public record Extra(
            double vx,
            double vz,
            int floor,
            boolean rising,
            double aimX,
            double aimZ,
            double best,
            int stuck,
            boolean latched,
            double latchX,
            double latchZ,
            int pushed) {
        /** A unit that never touched terrain. */
        public static final Extra NONE =
                new Extra(0, 0, NO_FLOOR, false, 0, 0, Double.POSITIVE_INFINITY, 0, false, 0, 0, 0);
    }

    public void ensureCapacity(int size) {
        if (size > x.length) {
            int old = x.length;
            int capacity = Math.max(size, old * 2);
            x = Arrays.copyOf(x, capacity);
            y = Arrays.copyOf(y, capacity);
            z = Arrays.copyOf(z, capacity);
            yaw = Arrays.copyOf(yaw, capacity);
            vx = Arrays.copyOf(vx, capacity);
            vz = Arrays.copyOf(vz, capacity);
            floor = Arrays.copyOf(floor, capacity);
            Arrays.fill(floor, old, capacity, NO_FLOOR);
            rising = Arrays.copyOf(rising, capacity);
            aimX = Arrays.copyOf(aimX, capacity);
            aimZ = Arrays.copyOf(aimZ, capacity);
            best = Arrays.copyOf(best, capacity);
            Arrays.fill(best, old, capacity, Double.POSITIVE_INFINITY);
            stuck = Arrays.copyOf(stuck, capacity);
            latched = Arrays.copyOf(latched, capacity);
            latchX = Arrays.copyOf(latchX, capacity);
            latchZ = Arrays.copyOf(latchZ, capacity);
            pushed = Arrays.copyOf(pushed, capacity);
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

    public double vx(int i) {
        return vx[i];
    }

    public double vz(int i) {
        return vz[i];
    }

    public void setVelocity(int i, double vx, double vz) {
        this.vx[i] = vx;
        this.vz[i] = vz;
    }

    /** The floor of the surface the unit stands on, in 1/16 above the band's bottom, or {@link #NO_FLOOR}. */
    public int floor(int i) {
        return floor[i];
    }

    public void setFloor(int i, int floor) {
        this.floor[i] = floor;
    }

    /** Whether the unit is halfway up a step: its Y reaches the new floor on its next move. */
    public boolean rising(int i) {
        return rising[i];
    }

    public void setRising(int i, boolean rising) {
        this.rising[i] = rising;
    }

    /** The point the stuck monitor measures progress toward: the unit's current waypoint or target. */
    public double aimX(int i) {
        return aimX[i];
    }

    public double aimZ(int i) {
        return aimZ[i];
    }

    /** The stuck monitor's best distance to its aim so far. */
    public double best(int i) {
        return best[i];
    }

    /** Ticks since the unit last came 0.05 block nearer its waypoint than its best. */
    public int stuck(int i) {
        return stuck[i];
    }

    public void setProgress(int i, double aimX, double aimZ, double best, int stuck) {
        this.aimX[i] = aimX;
        this.aimZ[i] = aimZ;
        this.best[i] = best;
        this.stuck[i] = stuck;
    }

    public boolean latched(int i) {
        return latched[i];
    }

    public double latchX(int i) {
        return latchX[i];
    }

    public double latchZ(int i) {
        return latchZ[i];
    }

    /**
     * Ticks a unit that stepped aside for others waits, quiet, before it may walk back to its latch point; 0 when it
     * was never pushed or has waited.
     */
    public int pushed(int i) {
        return pushed[i];
    }

    public void setPushed(int i, int ticks) {
        pushed[i] = ticks;
    }

    /** The arrival latch holds the unit to (x, z); {@code latched} false releases it. */
    public void setLatch(int i, boolean latched, double x, double z) {
        this.latched[i] = latched;
        this.latchX[i] = x;
        this.latchZ[i] = z;
    }

    /** The terrain part of unit {@code i}'s motion. */
    public Extra extra(int i) {
        return new Extra(
                vx[i],
                vz[i],
                floor[i],
                rising[i],
                aimX[i],
                aimZ[i],
                best[i],
                stuck[i],
                latched[i],
                latchX[i],
                latchZ[i],
                pushed[i]);
    }

    public void setExtra(int i, Extra e) {
        vx[i] = e.vx();
        vz[i] = e.vz();
        floor[i] = e.floor();
        rising[i] = e.rising();
        aimX[i] = e.aimX();
        aimZ[i] = e.aimZ();
        best[i] = e.best();
        stuck[i] = e.stuck();
        latched[i] = e.latched();
        latchX[i] = e.latchX();
        latchZ[i] = e.latchZ();
        pushed[i] = e.pushed();
    }

    /** Whether unit {@code i}'s terrain part is still {@link Extra#NONE}'s: it never touched terrain. */
    public boolean untouched(int i) {
        return floor[i] == NO_FLOOR
                && vx[i] == 0
                && vz[i] == 0
                && !rising[i]
                && aimX[i] == 0
                && aimZ[i] == 0
                && best[i] == Double.POSITIVE_INFINITY
                && stuck[i] == 0
                && !latched[i]
                && pushed[i] == 0;
    }
}
