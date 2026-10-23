package dev.strategos.core.sim.nav;

import java.util.List;

/**
 * What a {@link PathJob} found for one request: its status, the waypoints, and the expansions it spent; for a group's
 * request (PATH-09), also each member's own path, built from the group's ({@link GroupPaths}).
 */
public record PathResult(
        int requestId, PathStatus status, PathStore.Waypoints waypoints, int expansions, List<MemberPath> members) {
    public PathResult {
        members = List.copyOf(members);
    }

    /** A single unit's result. */
    public PathResult(int requestId, PathStatus status, PathStore.Waypoints waypoints, int expansions) {
        this(requestId, status, waypoints, expansions, List.of());
    }
}
