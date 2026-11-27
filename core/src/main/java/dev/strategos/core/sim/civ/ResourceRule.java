package dev.strategos.core.sim.civ;

import dev.strategos.core.shared.CoreApi;

/**
 * Which of the match's resources a faction uses (ADR-0028): a gatherer does not take one it does not, and the HUD does
 * not list it. The default is every resource.
 */
@CoreApi
@FunctionalInterface
public interface ResourceRule {
    ResourceRule DEFAULT = (faction, resource) -> true;

    /** Whether {@code faction} uses the match resource at index {@code resource}. */
    boolean uses(int faction, int resource);
}
