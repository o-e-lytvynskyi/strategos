package dev.strategos.core.shared;

/**
 * Deterministic maths: StrictMath for transcendental functions, so every JVM and platform gives the same bits;
 * Math.sqrt is correctly rounded by IEEE 754, so it may stay. Rule B4c bans the transcendental Math calls elsewhere.
 */
public final class DetMath {
    private DetMath() {}

    public static double sin(double a) {
        return StrictMath.sin(a);
    }

    public static double cos(double a) {
        return StrictMath.cos(a);
    }

    public static double atan2(double y, double x) {
        return StrictMath.atan2(y, x);
    }

    /** {@code a} to the power {@code b}, as StrictMath computes it: a resistance's 0.9 to its level. */
    public static double pow(double a, double b) {
        return StrictMath.pow(a, b);
    }

    public static double log(double v) {
        return StrictMath.log(v);
    }

    public static double sqrt(double v) {
        return Math.sqrt(v);
    }

    /** {@code sqrt(dx*dx + dz*dz)}; never {@code Math.hypot}, whose result may differ between platforms. */
    public static double length(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Minecraft's yaw for a heading: 0 faces +Z, 90 faces -X, -90 faces +X, -180 faces -Z; in [-180, 180). */
    public static float yawDegrees(double dx, double dz) {
        return (float) wrapDegrees(atan2(dz, dx) * 180 / Math.PI - 90);
    }

    /** {@code d} in [-180, 180). */
    public static double wrapDegrees(double d) {
        double w = d % 360.0;
        if (w >= 180.0) {
            w -= 360.0;
        } else if (w < -180.0) {
            w += 360.0;
        }
        return w;
    }
}
