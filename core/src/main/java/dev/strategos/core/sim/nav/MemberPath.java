package dev.strategos.core.sim.nav;

import dev.strategos.core.shared.CoreApi;

/**
 * One member's share of a group's result (PATH-09): where it stood when the group's job was made, how it joined the
 * group's path ({@link #LINE} by line of walk, {@link #SEARCH} by a capped search, {@link #NONE} not at all, so it asks
 * for a path of its own), and its waypoints.
 */
@CoreApi
public record MemberPath(
        int handle, int fromNode, double fromX, double fromZ, int join, PathStore.Waypoints waypoints) {
    public static final int NONE = 0;
    public static final int LINE = 1;
    public static final int SEARCH = 2;

    public MemberPath {
        if (join < NONE || join > SEARCH) {
            throw new IllegalArgumentException("join " + join + " outside 0..2");
        }
    }
}
