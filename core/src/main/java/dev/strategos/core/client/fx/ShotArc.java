package dev.strategos.core.client.fx;

/**
 * The path of a shot's arrow (CMB-04): a straight line from the release point to the landing point, lifted by a
 * parabola whose apex is {@value #LIFT} of the horizontal distance, nothing for a shot that does not travel
 * sideways. The position and the tangent are written into caller arrays so a frame allocates none.
 */
public record ShotArc(double fromX, double fromY, double fromZ, double toX, double toY, double toZ) {
    /** The apex's height over the straight line, per block of horizontal distance. */
    public static final double LIFT = 0.05;

    /** Writes the position at fraction {@code f} (clamped to 0..1) of the flight into {@code out} as x, y, z. */
    public void position(double f, double[] out) {
        double t = clamp(f);
        out[0] = fromX + (toX - fromX) * t;
        out[1] = fromY + (toY - fromY) * t + 4 * apex() * t * (1 - t);
        out[2] = fromZ + (toZ - fromZ) * t;
    }

    /**
     * Writes the unit direction of travel at fraction {@code f} into {@code out}; straight down for a shot whose ends
     * coincide, since it has no other direction.
     */
    public void tangent(double f, double[] out) {
        double t = clamp(f);
        double dx = toX - fromX;
        double dy = toY - fromY + 4 * apex() * (1 - 2 * t);
        double dz = toZ - fromZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-9) {
            out[0] = 0;
            out[1] = -1;
            out[2] = 0;
            return;
        }
        out[0] = dx / length;
        out[1] = dy / length;
        out[2] = dz / length;
    }

    private double apex() {
        return LIFT * Math.hypot(toX - fromX, toZ - fromZ);
    }

    private static double clamp(double f) {
        return Math.max(0, Math.min(1, f));
    }
}
