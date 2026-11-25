package dev.strategos.core.content;

import java.util.List;

/**
 * A unit class's ranks as its template declares them under {@code Promotion} (CMB-11): the experience totals at which
 * rank 1, 2, ... is reached, and for each rank the modifiers that rank gives. {@code thresholds} are in milli-experience
 * ({@link #SCALE} per point), ascending; {@code ranks} has one entry per threshold, the modifiers the rank holds
 * (empty for an insignia only). A rank's set replaces the one before it, so a set is written whole, never as an
 * increment. {@code Promotion/value}, what the unit is worth to whoever hurts it, is an ordinary number path.
 */
public record PromotionDef(List<Long> thresholds, List<List<Mod>> ranks) {
    /** Experience is kept in thousandths of a point, so damage shares never lose a hit point to rounding. */
    public static final int SCALE = 1000;

    /** One modifier of a rank, as the data names it: {@code op} is one of {@link AbilityKinds#modifierOps()}. */
    public record Mod(String path, String op, double value) {}

    public PromotionDef {
        thresholds = List.copyOf(thresholds);
        ranks = ranks.stream().map(List::copyOf).toList();
    }

    /** How many ranks above the first (unranked) there are. */
    public int count() {
        return thresholds.size();
    }
}
