package dev.strategos.core.content;

/**
 * The value paths the core itself reads, and the ranges paths are declared to (04 §8.1): a modifier's result is clamped
 * to its path's range. Every set has the core's paths, at the typed default where a template's JSON leaves one out. A
 * walk speed is at most a block a tick, the most the motion integrator steps; a turn rate at most half a turn. Paths the
 * core does not read are unbounded.
 */
public final class CorePaths {
    public static final String WALK_SPEED = "UnitMotion/walkSpeed";
    public static final String TURN_RATE = "UnitMotion/turnRate";

    private CorePaths() {}

    public static double min(String path) {
        return switch (path) {
            case WALK_SPEED, TURN_RATE -> 0;
            default -> Double.NEGATIVE_INFINITY;
        };
    }

    public static double max(String path) {
        return switch (path) {
            case WALK_SPEED -> 1;
            case TURN_RATE -> 180;
            default -> Double.POSITIVE_INFINITY;
        };
    }
}
