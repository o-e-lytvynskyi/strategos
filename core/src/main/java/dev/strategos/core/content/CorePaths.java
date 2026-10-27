package dev.strategos.core.content;

/**
 * The value paths the core itself reads, and the ranges paths are declared to (04 §8.1): a modifier's result is clamped
 * to its path's range. Every set has the core's paths, at the typed default where a template's JSON leaves one out. A
 * walk speed is at most a block a tick, the most the motion integrator steps; a turn rate at most half a turn. Health is
 * at least 1, a resistance level within ±20 (0.9^20 lets 12 % through, 0.9^-20 eight times as much, CMB-01). A melee
 * attack (CMB-02) deals its hack, pierce and crush at its reach, in blocks between the two bodies' edges, a wind-up
 * after it starts and then every period, in ticks; a unit whose amounts are all 0 has none. A unit's vision range is
 * where it notices enemies to fight, and its flee distance how far a unit with no melee attack runs from a blow (0 to 32
 * blocks). Paths the core does not read are unbounded.
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
    public static final String RANGED_HACK = "Attack/Ranged/hack";
    public static final String RANGED_PIERCE = "Attack/Ranged/pierce";
    public static final String RANGED_CRUSH = "Attack/Ranged/crush";
    public static final String RANGED_FIRE = "Attack/Ranged/fire";
    public static final String RANGED_RANGE = "Attack/Ranged/range";
    public static final String RANGED_PREPARE = "Attack/Ranged/prepare";
    public static final String RANGED_REPEAT = "Attack/Ranged/repeat";
    public static final String RANGED_SPEED = "Attack/Ranged/speed";
    public static final String RANGED_SPREAD = "Attack/Ranged/spread";
    public static final String VISION = "Vision/range";
    /** How far a unit without a melee attack runs from a blow, in blocks; 0 stands (UNIT-04). */
    public static final String FLEE = "Behaviour/flee";

    public static final double DEFAULT_HEALTH = 100;
    public static final double MAX_RESISTANCE = 20;
    public static final double DEFAULT_REACH = 1;
    public static final double DEFAULT_PREPARE = 10;
    public static final double DEFAULT_REPEAT = 20;
    public static final double DEFAULT_RANGED_PREPARE = 16;
    public static final double DEFAULT_RANGED_REPEAT = 25;
    public static final double DEFAULT_RANGED_SPEED = 4;
    public static final double DEFAULT_RANGED_SPREAD = 2.25;
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
                RANGED_HACK,
                RANGED_PIERCE,
                RANGED_CRUSH,
                RANGED_FIRE,
                RANGED_RANGE,
                RANGED_PREPARE,
                RANGED_REPEAT,
                RANGED_SPEED,
                RANGED_SPREAD,
                VISION,
                FLEE);
    }

    private CorePaths() {}

    public static double min(String path) {
        return switch (path) {
            case WALK_SPEED,
                    TURN_RATE,
                    MELEE_HACK,
                    MELEE_PIERCE,
                    MELEE_CRUSH,
                    MELEE_REACH,
                    MELEE_PREPARE,
                    RANGED_HACK,
                    RANGED_PIERCE,
                    RANGED_CRUSH,
                    RANGED_FIRE,
                    RANGED_RANGE,
                    RANGED_PREPARE,
                    RANGED_SPREAD,
                    VISION,
                    FLEE -> 0;
            case HEALTH, MELEE_REPEAT, RANGED_REPEAT -> 1;
            case RANGED_SPEED -> 0.01;
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
            case RANGED_RANGE -> 128;
            case RANGED_PREPARE, RANGED_REPEAT -> 200;
            case RANGED_SPEED -> 64;
            case RANGED_SPREAD -> 100;
            case RANGED_HACK, RANGED_PIERCE, RANGED_CRUSH, RANGED_FIRE -> Float.MAX_VALUE;
            case VISION -> 64;
            case FLEE -> 32;
            case RESIST_HACK, RESIST_PIERCE, RESIST_CRUSH -> MAX_RESISTANCE;
            default -> Double.POSITIVE_INFINITY;
        };
    }
}
