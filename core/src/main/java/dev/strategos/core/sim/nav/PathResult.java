package dev.strategos.core.sim.nav;

/** What a {@link PathJob} found for one request: its status, the waypoints, and the expansions it spent. */
public record PathResult(int requestId, PathStatus status, PathStore.Waypoints waypoints, int expansions) {}
