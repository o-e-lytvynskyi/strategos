package dev.strategos.core.sim.world;

import dev.strategos.core.shared.CoreApi;

/**
 * Where writes come from: a match ({@code 0} for the world itself, such as an arena's lobby pad) and a site in it, such
 * as a building or a resource node. {@link WriteScheduler} serves sites in this order: match, then site.
 */
@CoreApi
public record WriteSite(int match, int site) implements Comparable<WriteSite> {
    @Override
    public int compareTo(WriteSite other) {
        int byMatch = Integer.compare(match, other.match);
        return byMatch != 0 ? byMatch : Integer.compare(site, other.site);
    }
}
