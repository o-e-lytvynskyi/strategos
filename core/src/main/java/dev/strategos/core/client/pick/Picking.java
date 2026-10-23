package dev.strategos.core.client.pick;

import org.jspecify.annotations.Nullable;

/**
 * Rays and projections for a frame (CAM-04, 04 §13.4). The cursor comes in GUI pixels, as a screen's mouse events give
 * it, and becomes window points by the window's size over the GUI's; NDC is then (2x/w − 1, 1 − 2y/h) over the window's
 * points, never its framebuffer pixels.
 */
public final class Picking {
    private Picking() {}

    /** A ray: its origin and unit direction. */
    public record Ray(double ox, double oy, double oz, double dx, double dy, double dz) {
        /** The point {@code t} blocks along. */
        public double[] at(double t) {
            return new double[] {ox + dx * t, oy + dy * t, oz + dz * t};
        }
    }

    /** A cursor in GUI pixels as window points, for a GUI {@code guiWidth} wide in a window {@code width} points wide. */
    public static double toWindow(double gui, double guiExtent, double windowExtent) {
        return gui * windowExtent / guiExtent;
    }

    /** The ray through a cursor at window points ({@code x}, {@code y}). */
    public static Ray ray(View view, double x, double y) {
        double nx = 2 * x / view.width() - 1;
        double ny = 1 - 2 * y / view.height();
        double[] inverse = Mat4.invert(Mat4.mul(view.projection(), view.modelView()));
        double[] near = Mat4.transform(inverse, nx, ny, -1, 1);
        double[] far = Mat4.transform(inverse, nx, ny, 1, 1);
        double ax = near[0] / near[3];
        double ay = near[1] / near[3];
        double az = near[2] / near[3];
        double bx = far[0] / far[3] - ax;
        double by = far[1] / far[3] - ay;
        double bz = far[2] / far[3] - az;
        double length = Math.sqrt(bx * bx + by * by + bz * bz);
        return new Ray(view.camX() + ax, view.camY() + ay, view.camZ() + az, bx / length, by / length, bz / length);
    }

    /** A world point's window points, or null when it is behind the camera. */
    public static double @Nullable [] project(View view, double x, double y, double z) {
        double[] clip = Mat4.transform(
                Mat4.mul(view.projection(), view.modelView()), x - view.camX(), y - view.camY(), z - view.camZ(), 1);
        if (clip[3] <= 0) {
            return null;
        }
        return new double[] {(clip[0] / clip[3] + 1) / 2 * view.width(), (1 - clip[1] / clip[3]) / 2 * view.height()};
    }

    /** Where the ray enters a box, in blocks along it, or -1 when it misses (the slab method). */
    public static double hit(Ray ray, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double near = 0;
        double far = Double.POSITIVE_INFINITY;
        double[] o = {ray.ox(), ray.oy(), ray.oz()};
        double[] d = {ray.dx(), ray.dy(), ray.dz()};
        double[] lo = {minX, minY, minZ};
        double[] hi = {maxX, maxY, maxZ};
        for (int k = 0; k < 3; k++) {
            if (Math.abs(d[k]) < 1e-12) {
                if (o[k] < lo[k] || o[k] > hi[k]) {
                    return -1;
                }
                continue;
            }
            double t1 = (lo[k] - o[k]) / d[k];
            double t2 = (hi[k] - o[k]) / d[k];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
            if (near > far) {
                return -1;
            }
        }
        return near;
    }
}
