package dev.strategos.mod.server.match;

import dev.strategos.core.port.MetricsSink;
import org.jspecify.annotations.Nullable;

/**
 * How a match is made: its seed (random when null), factions, whether it is saved, who made it, its fault policy (the
 * server's default when null), its metrics sink and the Y its units stand at.
 */
public record MatchOptions(
        @Nullable Long seed,
        int factions,
        boolean persistent,
        MatchSource source,
        @Nullable FaultPolicy faultPolicy,
        MetricsSink metrics,
        double flatFloorY) {
    /** A saved match with a random seed and two factions. */
    public static MatchOptions generated(double flatFloorY) {
        return new MatchOptions(null, 2, true, MatchSource.GENERATED, null, MetricsSink.none(), flatFloorY);
    }

    /** An unsaved match with a fixed seed and two factions. */
    public static MatchOptions gameTest(long seed, double flatFloorY) {
        return new MatchOptions(seed, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), flatFloorY);
    }

    public MatchOptions withPersistent(boolean persistent) {
        return new MatchOptions(seed, factions, persistent, source, faultPolicy, metrics, flatFloorY);
    }

    public MatchOptions withFaultPolicy(FaultPolicy faultPolicy) {
        return new MatchOptions(seed, factions, persistent, source, faultPolicy, metrics, flatFloorY);
    }

    public MatchOptions withMetrics(MetricsSink metrics) {
        return new MatchOptions(seed, factions, persistent, source, faultPolicy, metrics, flatFloorY);
    }

    public MatchOptions withSeed(long seed) {
        return new MatchOptions(seed, factions, persistent, source, faultPolicy, metrics, flatFloorY);
    }
}
