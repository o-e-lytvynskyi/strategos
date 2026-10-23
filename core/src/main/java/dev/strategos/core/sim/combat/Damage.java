package dev.strategos.core.sim.combat;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.Handle;
import java.util.List;

/**
 * One damage packet for the COMBAT phase (CMB-01): hack, pierce and crush amounts from {@code attacker} ({@link
 * Handle#NONE} for a vanilla source) to {@code target}, the attacker's bonus multipliers against classes, or lethal,
 * which takes all that is left. Packets of a tick apply in the order they were queued.
 */
@CoreApi
public record Damage(
        int target, int attacker, float hack, float pierce, float crush, List<Bonus> bonuses, boolean lethal) {
    /** {@code multiplier} on the whole packet when the target has class {@code token}. */
    @CoreApi
    public record Bonus(String token, float multiplier) {
        public Bonus {
            if (!(multiplier >= 0) || Float.isInfinite(multiplier)) {
                throw new IllegalArgumentException("bonus " + multiplier + " against " + token + " is not finite");
            }
        }
    }

    public Damage {
        for (float a : new float[] {hack, pierce, crush}) {
            if (!(a >= 0) || Float.isInfinite(a)) {
                throw new IllegalArgumentException("damage " + a + " is not a finite amount");
            }
        }
        bonuses = List.copyOf(bonuses);
    }

    /** A packet of one type, with no bonuses. */
    public static Damage of(int target, int attacker, DamageType type, float amount) {
        return new Damage(
                target,
                attacker,
                type == DamageType.HACK ? amount : 0,
                type == DamageType.PIERCE ? amount : 0,
                type == DamageType.CRUSH ? amount : 0,
                List.of(),
                false);
    }

    /** What is left of the target, whatever its resistances. */
    public static Damage lethal(int target, int attacker) {
        return new Damage(target, attacker, 0, 0, 0, List.of(), true);
    }

    public float amount(DamageType type) {
        return switch (type) {
            case HACK -> hack;
            case PIERCE -> pierce;
            case CRUSH -> crush;
        };
    }
}
