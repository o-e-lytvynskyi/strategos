package dev.strategos.core.client.camera;

import org.jspecify.annotations.Nullable;

/**
 * The RTS camera (CAM-02, ADR-0017, 04 §13.2): a focus on the ground, a yaw, a pitch of 35–70° (default 55°), a height
 * of 8–64 blocks above the focus ground (default 24) and a FOV of 55°. Each client tick it steps once: pan, rotation and
 * zoom move targets, the state eases toward them, the focus follows the terrain and stays inside the limits, and the eye
 * moves at most {@value #MAX_EYE_STEP} blocks, the pan cap spike S9 set, below the dedicated server's movement check.
 * Frames interpolate between the last two ticks, so a pose depends on ticks, never on the frame rate.
 */
public final class CameraRig {
    public static final double MIN_PITCH = 35;
    public static final double MAX_PITCH = 70;
    public static final double DEFAULT_PITCH = 55;
    public static final double MIN_HEIGHT = 8;
    public static final double MAX_HEIGHT = 64;
    public static final double DEFAULT_HEIGHT = 24;
    public static final double FOV = 55;
    /** S9: a movement packet may move 10 × √k blocks for k packets in a server tick; 5 is safe for four. */
    public static final double MAX_EYE_STEP = 5;
    /** Pan speed per tick as a fraction of the height: 24 blocks up, 2.4 blocks a tick, 48 a second. */
    private static final double PAN_PER_HEIGHT = 0.1;

    private static final double ROTATE_PER_TICK = 4.5;
    private static final double ZOOM_PER_NOTCH = 1.15;
    private static final double EASE = 0.35;
    private static final double GROUND_EASE = 0.25;

    private final CameraLimits limits;
    private double focusX;
    private double focusZ;
    private double ground;
    private double yaw;
    private double yawTarget;
    private double height = DEFAULT_HEIGHT;
    private double heightTarget = DEFAULT_HEIGHT;
    private double pitch = DEFAULT_PITCH;
    private CameraPose last;
    private double @Nullable [] panTarget;
    private CameraPose current;

    public CameraRig(CameraLimits limits, double focusX, double focusZ, double ground, double yaw) {
        this.limits = limits;
        this.focusX = limits.clampX(focusX);
        this.focusZ = limits.clampZ(focusZ);
        this.ground = ground;
        this.yaw = yaw;
        this.yawTarget = yaw;
        this.current = eye();
        this.last = current;
    }

    /** The terrain's height at a column, for the focus to follow. */
    @FunctionalInterface
    public interface Ground {
        double at(double x, double z);
    }

    /** One client tick: the input moves the targets, the state eases toward them within the eye's step. */
    public void tick(CameraInput in, Ground terrain) {
        last = current;
        double speed = PAN_PER_HEIGHT * height * in.panSpeed();
        double radians = Math.toRadians(yaw);
        double fx = -Math.sin(radians);
        double fz = Math.cos(radians);
        double nextX = limits.clampX(focusX + (in.forward() * fx - in.right() * fz) * speed);
        double nextZ = limits.clampZ(focusZ + (in.forward() * fz + in.right() * fx) * speed);
        double[] glide = panTarget;
        if (glide != null) { // a pan to a point: the cap below keeps it to 5 blocks a tick
            if (in.forward() != 0 || in.right() != 0 || Math.hypot(glide[0] - focusX, glide[1] - focusZ) < 0.5) {
                panTarget = null;
            } else {
                nextX = limits.clampX(glide[0]);
                nextZ = limits.clampZ(glide[1]);
            }
        }
        yawTarget += in.rotate() * ROTATE_PER_TICK * in.rotateSpeed() + in.dragDegrees();
        if (in.zoomNotches() != 0) {
            heightTarget = clamp(heightTarget / Math.pow(ZOOM_PER_NOTCH, in.zoomNotches()), MIN_HEIGHT, MAX_HEIGHT);
        }
        double nextYaw = yaw + (yawTarget - yaw) * EASE;
        double nextHeight = height + (heightTarget - height) * EASE;
        double nextGround = ground + (terrain.at(nextX, nextZ) - ground) * GROUND_EASE;
        double[] from = {focusX, focusZ, yaw, height, ground};
        double[] to = {nextX, nextZ, nextYaw, nextHeight, nextGround};
        if (eye(to).eyeDistance(current) > MAX_EYE_STEP) { // the eye goes part of the way, the rest on later ticks
            double low = 0;
            double high = 1;
            for (int k = 0; k < 24; k++) { // the eye's path is not linear in the state (rotation), so bisect
                double mid = (low + high) / 2;
                if (eye(between(from, to, mid)).eyeDistance(current) <= MAX_EYE_STEP) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            to = between(from, to, low);
        }
        focusX = to[0];
        focusZ = to[1];
        yaw = to[2];
        height = to[3];
        ground = to[4];
        current = eye();
    }

    /** The pose {@code partial} of the way from the last tick to the current, for a frame. */
    public CameraPose pose(double partial) {
        double p = clamp(partial, 0, 1);
        return new CameraPose(
                last.x() + (current.x() - last.x()) * p,
                last.y() + (current.y() - last.y()) * p,
                last.z() + (current.z() - last.z()) * p,
                last.yaw() + (current.yaw() - last.yaw()) * p,
                last.pitch() + (current.pitch() - last.pitch()) * p,
                FOV);
    }

    /** The current tick's pose: where the player stands as the camera. */
    public CameraPose pose() {
        return current;
    }

    /**
     * Pans the focus to a point at the eye's cap, 5 blocks a tick, as centring on a selection or a group does without a
     * server jump; a pan key stops it.
     */
    public void panTo(double x, double z) {
        panTarget = new double[] {x, z};
    }

    /** Puts the focus at a point, as a jump does; the eye moves with it, so the next tick starts there. */
    public void jumpTo(double x, double z, double groundAt) {
        focusX = limits.clampX(x);
        focusZ = limits.clampZ(z);
        ground = groundAt;
        current = eye();
        last = current;
    }

    public double focusX() {
        return focusX;
    }

    public double focusZ() {
        return focusZ;
    }

    public double height() {
        return height;
    }

    private CameraPose eye() {
        return eye(new double[] {focusX, focusZ, yaw, height, ground});
    }

    /** The eye above and behind the focus: {@code height} up, {@code height / tan(pitch)} back along the view. */
    private CameraPose eye(double[] s) {
        double radians = Math.toRadians(s[2]);
        double back = s[3] / Math.tan(Math.toRadians(pitch));
        return new CameraPose(
                s[0] + Math.sin(radians) * back, s[4] + s[3], s[1] - Math.cos(radians) * back, s[2], pitch, FOV);
    }

    private static double[] between(double[] from, double[] to, double f) {
        double[] out = new double[from.length];
        for (int k = 0; k < from.length; k++) {
            out[k] = from[k] + (to[k] - from[k]) * f;
        }
        return out;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
