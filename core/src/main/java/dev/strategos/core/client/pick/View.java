package dev.strategos.core.client.pick;

/**
 * One frame's view for picking: its projection and model-view matrices (column-major; the model-view only rotates,
 * so positions are taken relative to the camera), the camera's position, and the window's size in points.
 */
public final class View {
    private final double[] projection;
    private final double[] modelView;
    private final double camX;
    private final double camY;
    private final double camZ;
    private final double width;
    private final double height;

    public View(
            double[] projection,
            double[] modelView,
            double camX,
            double camY,
            double camZ,
            double width,
            double height) {
        this.projection = projection.clone();
        this.modelView = modelView.clone();
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.width = width;
        this.height = height;
    }

    public double[] projection() {
        return projection.clone();
    }

    public double[] modelView() {
        return modelView.clone();
    }

    public double camX() {
        return camX;
    }

    public double camY() {
        return camY;
    }

    public double camZ() {
        return camZ;
    }

    /** The window's width in points. */
    public double width() {
        return width;
    }

    /** The window's height in points. */
    public double height() {
        return height;
    }
}
