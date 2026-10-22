package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;

/**
 * A body the core does not own that units steer around (PATH-06, 04 §9.5): a first-person player or a vanilla mob in
 * the match space, as the server saw it this tick. X and Z are the match's own coordinates, Y the world's; the
 * velocity is in blocks a tick. Units count it as non-reactive: it never makes way for them.
 */
@CoreApi
public record Agent(int id, float x, float y, float z, float vx, float vz, float radius) {
    /** The largest radius an agent may have, in blocks. */
    public static final float MAX_RADIUS = 2;

    public Agent {
        if (!Float.isFinite(x)
                || !Float.isFinite(y)
                || !Float.isFinite(z)
                || !Float.isFinite(vx)
                || !Float.isFinite(vz)) {
            throw new IllegalArgumentException("agent " + id + " has a coordinate or velocity that is not finite");
        }
        if (!(radius > 0 && radius <= MAX_RADIUS)) {
            throw new IllegalArgumentException(
                    "agent " + id + " has radius " + radius + ", not in (0, " + MAX_RADIUS + "]");
        }
    }
}
