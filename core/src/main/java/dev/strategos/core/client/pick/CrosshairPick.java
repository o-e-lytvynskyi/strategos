package dev.strategos.core.client.pick;

import java.util.List;

/**
 * What the first-person crosshair points at (CAM-07.2): the ray from the eye along the look, and of the bodies it
 * enters, the nearest that lies before the first block it hits, within {@value #REACH} blocks. Buildings join the
 * candidates with their card (M3).
 */
public final class CrosshairPick {
    /** How far the crosshair reaches, as far as G's move does. */
    public static final double REACH = 128;

    /** A body that can be picked: its handle and its box. */
    public record Body(int handle, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    private CrosshairPick() {}

    /** The ray from an eye at (x, y, z) along Minecraft's yaw and pitch, in degrees. */
    public static Picking.Ray ray(double x, double y, double z, double yaw, double pitch) {
        double ry = Math.toRadians(yaw);
        double rp = Math.toRadians(pitch);
        double cos = Math.cos(rp);
        return new Picking.Ray(x, y, z, -Math.sin(ry) * cos, -Math.sin(rp), Math.cos(ry) * cos);
    }

    /**
     * The handle of the nearest of {@code bodies} the ray enters closer than {@code block}, the distance to the first
     * block it hits (infinite for none), within {@link #REACH}; 0 for none.
     */
    public static int pick(Picking.Ray ray, List<Body> bodies, double block) {
        double best = Math.min(block, REACH);
        int found = 0;
        for (Body b : bodies) {
            double t = Picking.hit(ray, b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
            if (t >= 0 && t < best) {
                best = t;
                found = b.handle();
            }
        }
        return found;
    }
}
