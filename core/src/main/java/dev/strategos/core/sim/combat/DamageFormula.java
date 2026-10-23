package dev.strategos.core.sim.combat;

import dev.strategos.core.shared.DetMath;
import java.util.List;

/**
 * The damage a packet deals (CMB-01, mechanics §1.8): each type's amount times 0.9 to the target's resistance level
 * against it, summed, then times every bonus whose class the target has. A negative level lets more through.
 */
public final class DamageFormula {
    public static final double RESIST_BASE = 0.9;

    private DamageFormula() {}

    /** {@code resistance} holds the target's levels by {@link DamageType#index()}; {@code classes} its tokens. */
    public static double dealt(Damage d, double[] resistance, List<String> classes) {
        double sum = 0;
        for (DamageType type : DamageType.values()) {
            float amount = d.amount(type);
            if (amount > 0) {
                sum += amount * DetMath.pow(RESIST_BASE, resistance[type.index()]);
            }
        }
        for (Damage.Bonus b : d.bonuses()) {
            if (classes.contains(b.token())) {
                sum *= b.multiplier();
            }
        }
        return sum;
    }
}
