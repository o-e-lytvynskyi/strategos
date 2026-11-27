package dev.strategos.core.sim.civ;

import dev.strategos.core.protocol.NoticeKind;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.sim.prod.Population;

/**
 * A faction's population cap and why a product that does not fit is refused (ADR-0028): the one source of the cap, asked
 * by production's reservation, the HUD's replication and the AI. The counters (used, housing) stay in {@link Population};
 * production keeps the cost reservation and the waiting flag. The default is {@code min(housing + base, the match's
 * maximum)}, refused with {@code HOUSING_NEEDED}.
 */
@CoreApi
public interface PopulationRule {
    /** Today's cap: housing alone, no base. */
    PopulationRule DEFAULT = housing(0);

    /** The most population the faction may use now. */
    int cap(Population pop, int faction);

    /** The notice that says why a product that does not fit waits. */
    NoticeKind refusal();

    /**
     * The AI hints this rule implies, a mask of {@link AiHint}s (the default needs housing). A later variant brings its
     * own hints by overriding this method; {@link CivRules} combines the seams' masks once at bind.
     */
    default int hints() {
        return AiHint.NEEDS_HOUSING.mask();
    }

    /** Whether {@code amount} more fits under the faction's cap. */
    default boolean fits(Population pop, int faction, int amount) {
        return pop.used(faction) + amount <= cap(pop, faction);
    }

    /** The cap grows with the housing of finished buildings, from {@code base}. */
    static PopulationRule housing(int base) {
        return new PopulationRule() {
            @Override
            public int cap(Population pop, int faction) {
                return Math.min(pop.housing(faction) + base, pop.max());
            }

            @Override
            public NoticeKind refusal() {
                return NoticeKind.HOUSING_NEEDED;
            }
        };
    }

    /** The cap is {@code cap}, whatever is housed. */
    static PopulationRule fixed(int cap) {
        return new PopulationRule() {
            @Override
            public int cap(Population pop, int faction) {
                return Math.min(cap, pop.max());
            }

            @Override
            public NoticeKind refusal() {
                return NoticeKind.POPULATION_CAP;
            }

            @Override
            public int hints() {
                return 0; // no house raises the cap: the AI builds none
            }
        };
    }
}
