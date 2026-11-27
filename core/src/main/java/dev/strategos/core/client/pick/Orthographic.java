package dev.strategos.core.client.pick;

/**
 * The orthographic RTS view (CAM-06, D-09). Its size follows the camera's height, so zooming works as in perspective:
 * the picture is as tall as the perspective view's at the eye's distance from the focus point, and the cursor is
 * picked by {@link Picking#ray}, which unprojects the near and far planes and so yields parallel rays here.
 */
public final class Orthographic {
    /** The near plane, as the perspective matrix has it. */
    public static final double NEAR = 0.05;

    private Orthographic() {}

    /**
     * The view's half height in blocks: the perspective view's half height at the focus point, {@code distance ·
     * tan(fov / 2)}, the eye {@code height / sin(pitch)} blocks from it.
     */
    public static double halfHeight(double height, double pitchDegrees, double fovDegrees) {
        double distance = height / Math.sin(Math.toRadians(pitchDegrees));
        return distance * Math.tan(Math.toRadians(fovDegrees) / 2);
    }

    /** The projection for a window {@code aspect} (width over height) wide: half width is half height times aspect. */
    public static double[] matrix(double height, double pitchDegrees, double fovDegrees, double aspect, double far) {
        double halfHeight = halfHeight(height, pitchDegrees, fovDegrees);
        return Mat4.orthographic(halfHeight * aspect, halfHeight, NEAR, far);
    }
}
