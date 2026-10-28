package dev.strategos.core.sim.combat;

import dev.strategos.core.sim.MatchState;

/**
 * High ground (CMB-16): a ranged attacker standing above its target reaches farther by {@code rangePerBlock} and hits
 * harder by {@code damagePerBlock} for every block of height, up to {@code maxBlocks}. The height is the difference of
 * the two units' motion Y, which on terrain is the floor they stand on; a flat match gives 0, so nothing changes.
 */
public final class HighGround {
    private HighGround() {}

    /** The blocks the shooter stands over the target, between 0 and the shooter's cap. */
    public static double advantage(MatchState s, int shooter, int target) {
        double delta = s.motion().y(shooter) - s.motion().y(target);
        return Math.max(0, Math.min(delta, s.stats().highGroundMax(shooter)));
    }

    /** The factor on the shooter's range for a height of {@code blocks}. */
    public static double rangeFactor(MatchState s, int shooter, double blocks) {
        return 1 + s.stats().highGroundRange(shooter) * blocks;
    }

    /** The factor on the shooter's damage for a height of {@code blocks}. */
    public static double damageFactor(MatchState s, int shooter, double blocks) {
        return 1 + s.stats().highGroundDamage(shooter) * blocks;
    }
}
