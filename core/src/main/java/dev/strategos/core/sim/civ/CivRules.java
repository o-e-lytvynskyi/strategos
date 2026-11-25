package dev.strategos.core.sim.civ;

import dev.strategos.core.content.PlacementRule;
import dev.strategos.core.shared.CoreApi;

/**
 * One faction's rule seams, resolved once from its civilisation's {@code rules} by the {@link RuleCatalogue} (ADR-0028):
 * an immutable record systems reach as {@code s.civs().rules(faction)}, an array index. A system calls a seam and never
 * tests a civilisation id.
 */
@CoreApi
public record CivRules(
        PopulationRule population,
        DeliveryRule delivery,
        ConstructionRule construction,
        UpkeepRule upkeep,
        ResourceRule resource,
        PhaseRule phase,
        PlacementRule placement,
        DefeatRule defeat,
        int hints) {
    /**
     * The seams with their AI hints combined here, once (no allocation per tick): the union of what the population,
     * delivery and construction rules declare in {@code hints()}.
     */
    public CivRules(
            PopulationRule population,
            DeliveryRule delivery,
            ConstructionRule construction,
            UpkeepRule upkeep,
            ResourceRule resource,
            PhaseRule phase,
            PlacementRule placement,
            DefeatRule defeat) {
        this(
                population,
                delivery,
                construction,
                upkeep,
                resource,
                phase,
                placement,
                defeat,
                population.hints() | delivery.hints() | construction.hints());
    }

    /** Whether the faction's rules imply {@code hint}: what the AI reads (never a rule id). */
    public boolean needs(AiHint hint) {
        return hint.in(hints);
    }

    /** Every seam at its default: a faction with no entry, and one outside the match (Gaia). */
    public static final CivRules DEFAULTS = new CivRules(
            PopulationRule.DEFAULT,
            DeliveryRule.DEFAULT,
            ConstructionRule.DEFAULT,
            UpkeepRule.DEFAULT,
            ResourceRule.DEFAULT,
            PhaseRule.DEFAULT,
            PlacementRule.DEFAULT,
            DefeatRule.DEFAULT);
}
