package dev.strategos.core.content;

import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TechId;

/** What a faction has, as far as {@link Requirements} asks (TECH-01). */
@CoreApi
public interface FactionView {
    /** Whether the faction has researched {@code tech}. */
    boolean researched(TechId tech);

    /** Whether the faction has reached phase {@code phase}. */
    boolean reached(String phase);

    /** How many of the faction's COMPLETE buildings match the class filter. */
    int completeBuildings(String classFilter);
}
