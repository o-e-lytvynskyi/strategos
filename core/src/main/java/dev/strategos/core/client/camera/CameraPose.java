package dev.strategos.core.client.camera;

/** Where the camera is and looks: the eye in world coordinates, yaw and pitch in degrees (Minecraft's), FOV. */
public record CameraPose(double x, double y, double z, double yaw, double pitch, double fov) {
    /** The distance from this pose's eye to another's. */
    public double eyeDistance(CameraPose other) {
        double dx = other.x - x;
        double dy = other.y - y;
        double dz = other.z - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
