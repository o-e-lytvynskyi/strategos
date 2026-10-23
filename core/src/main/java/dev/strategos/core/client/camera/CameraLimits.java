package dev.strategos.core.client.camera;

/** The area the focus may move in: the match space's corners, in world X and Z, grown by {@code margin}. */
public record CameraLimits(double minX, double minZ, double maxX, double maxZ, double margin) {
    public static final double MARGIN = 16;

    public double clampX(double x) {
        return Math.max(minX - margin, Math.min(maxX + margin, x));
    }

    public double clampZ(double z) {
        return Math.max(minZ - margin, Math.min(maxZ + margin, z));
    }
}
