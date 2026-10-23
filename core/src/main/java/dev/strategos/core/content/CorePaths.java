package dev.strategos.core.content;

/**
 * The value paths the core itself reads, and the ranges paths are declared to (04 §8.1): a modifier's result is clamped
 * to its path's range. Every set has the core's paths, at the typed default where a template's JSON leaves one out. A
 * walk speed is at most a block a tick, the most the motion integrator steps; a turn rate at most half a turn. Health is
 * at least 1, a resistance level within ±20 (0.9^20 lets 12 % through, 0.9^-20 eight times as much, CMB-01). A melee
 * attack (CMB-02) deals its hack, pierce and crush at its reach, in blocks between the two bodies' edges, a wind-up
 * after it starts and then every period, in ticks; a unit whose amounts are all 0 has none. A unit's vision range is
 * where it notices enemies to fight. Paths the core does not read are unbounded.
 */
public final class CorePaths {
    public static final String WALK_SPEED = "UnitMotion/walkSpeed";
    public static final String TURN_RATE = "UnitMotion/turnRate";
    public static final String HEALTH = "Health/max";
    public static final String RESIST_HACK = "Resistance/hack";
    public static final String RESIST_PIERCE = "Resistance/pierce";
    public static final String RESIST_CRUSH = "Resistance/crush";
    public static final String MELEE_HACK = "Attack/Melee/hack";
    public static final String MELEE_PIERCE = "Attack/Melee/pierce";
    public static final String MELEE_CRUSH = "Attack/Melee/crush";
    public static final String MELEE_REACH = "Attack/Melee/reach";
    public static final String MELEE_PREPARE = "Attack/Melee/prepare";
    public static final String MELEE_REPEAT = "Attack/Melee/repeat";
    public static final String VISION = "Vision/range";
    public static final double DEFAULT_HEALTH = 100;
    public static final double MAX_RESISTANCE = 20;
    public static final double DEFAULT_REACH = 1;
    public static final double DEFAULT_PREPARE = 10;
    public static final double DEFAULT_REPEAT = 20;
    public static final double DEFAULT_VISION = 12;

    /** Every path the core reads, which every set carries. */
    public static java.util.List<String> all() {
        return java.util.List.of(
                WALK_SPEED,
                TURN_RATE,
                HEALTH,
                RESIST_HACK,
                RESIST_PIERCE,
                RESIST_CRUSH,
                MELEE_HACK,
                MELEE_PIERCE,
                MELEE_CRUSH,
                MELEE_REACH,
                MELEE_PREPARE,
                MELEE_REPEAT,
                VISION);
    }

    private CorePaths() {}

    public static double min(String path) {
        return switch (path) {
            case WALK_SPEED, TURN_RATE, MELEE_HACK, MELEE_PIERCE, MELEE_CRUSH, MELEE_REACH, MELEE_PREPARE, VISION -> 0;
            case HEALTH, MELEE_REPEAT -> 1;
            case RESIST_HACK, RESIST_PIERCE, RESIST_CRUSH -> -MAX_RESISTANCE;
            default -> Double.NEGATIVE_INFINITY;
        };
    }

    public static double max(String path) {
        return switch (path) {
            case WALK_SPEED -> 1;
            case TURN_RATE -> 180;
            case MELEE_REACH -> 8;
            case MELEE_PREPARE, MELEE_REPEAT -> 200;
            case VISION -> 64;
            case RESIST_HACK, RESIST_PIERCE, RESIST_CRUSH -> MAX_RESISTANCE;
            default -> Double.POSITIVE_INFINITY;
        };
    }
}
