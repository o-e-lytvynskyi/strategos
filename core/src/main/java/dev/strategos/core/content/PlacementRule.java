package dev.strategos.core.content;

import dev.strategos.core.shared.CoreApi;

/**
 * The placement seam (ADR-0028): a civilisation's verdict on where a building may stand, a parameter of the pure
 * {@link PlacementRules} so the server's judgement and the client's preview run the same variant. Lives with the rules
 * it varies, in {@code content}, because the client reads it and may not depend on the simulation. The default is
 * today's checks, in the order {@link PlacementRules} tells them.
 */
@CoreApi
@FunctionalInterface
public interface PlacementRule {
    PlacementRule DEFAULT = PlacementRules::standard;

    /** The verdict for blueprint {@code b} at {@code at} for {@code faction}, as {@code params} asks. */
    PlacementRules.Verdict check(
            PlacementRules.World world, Blueprint b, Footprint at, int faction, PlacementRules.Params params);
}
