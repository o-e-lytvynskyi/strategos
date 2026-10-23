package dev.strategos.core.client.camera;

/**
 * One tick's camera input: pan along the view (forward, right) and rotation each in -1..1 from keys or the screen
 * edge, a middle drag's rotation in degrees, zoom notches (positive closer), and the player's speed multipliers for
 * panning and turning.
 */
public record CameraInput(
        double forward,
        double right,
        double rotate,
        double dragDegrees,
        double zoomNotches,
        double panSpeed,
        double rotateSpeed) {
    public static final CameraInput NONE = new CameraInput(0, 0, 0, 0, 0, 1, 1);

    /** At the default speeds. */
    public CameraInput(double forward, double right, double rotate, double dragDegrees, double zoomNotches) {
        this(forward, right, rotate, dragDegrees, zoomNotches, 1, 1);
    }
}
