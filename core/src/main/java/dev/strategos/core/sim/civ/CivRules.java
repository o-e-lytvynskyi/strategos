package dev.strategos.core.sim.civ;

import dev.strategos.core.content.PlacementRule;
import dev.strategos.core.shared.CoreApi;

/**
 * One faction's rule seams, resolved once from its civilisation's {@code rules} by the {@link RuleCatalogue} (ADR-0028):
 * an immutable record systems reach as {@code s.civs().rules(faction)}, an array index. A system calls a seam and never
 * tests a civilisation id. The population, delivery and construction seams join it with M10 T6 and T7.
 */
@CoreApi
public record CivRules(
        UpkeepRule upkeep, ResourceRule resource, PhaseRule phase, PlacementRule placement, DefeatRule defeat) {
    /** Every seam at its default: a faction with no entry, and one outside the match (Gaia). */
    public static final CivRules DEFAULTS = new CivRules(
            UpkeepRule.DEFAULT, ResourceRule.DEFAULT, PhaseRule.DEFAULT, PlacementRule.DEFAULT, DefeatRule.DEFAULT);
}
