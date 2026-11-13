package dev.strategos.mod.server.match;

import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.sim.PlayRules;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * How a match is made: its seed (random when null), factions, whether it is saved, who made it, its fault policy (the
 * server's default when null), its metrics sink, the Y its units stand at, the map package to import, if any (the
 * match then stays PREPARING until the import is done), and its play rules (MATCH-07's visibility option among them).
 */
public record MatchOptions(
        @Nullable Long seed,
        int factions,
        boolean persistent,
        MatchSource source,
        @Nullable FaultPolicy faultPolicy,
        MetricsSink metrics,
        double flatFloorY,
        @Nullable ResourceLocation map,
        PlayRules play) {
    /** GameTests not about fog: MATCH-07's Explored option, so placement and the observer's footing work anywhere. */
    public static final PlayRules GAMETEST_PLAY = new PlayRules(PlayRules.Victory.NONE, false, true, false);

    /** A match under the default play rules (Fog). */
    public MatchOptions(
            @Nullable Long seed,
            int factions,
            boolean persistent,
            MatchSource source,
            @Nullable FaultPolicy faultPolicy,
            MetricsSink metrics,
            double flatFloorY,
            @Nullable ResourceLocation map) {
        this(seed, factions, persistent, source, faultPolicy, metrics, flatFloorY, map, PlayRules.DEFAULTS);
    }

    /** A match without a map. */
    public MatchOptions(
            @Nullable Long seed,
            int factions,
            boolean persistent,
            MatchSource source,
            @Nullable FaultPolicy faultPolicy,
            MetricsSink metrics,
            double flatFloorY) {
        this(seed, factions, persistent, source, faultPolicy, metrics, flatFloorY, null);
    }

    /** A saved match with a random seed and two factions. */
    public static MatchOptions generated(double flatFloorY) {
        return new MatchOptions(null, 2, true, MatchSource.GENERATED, null, MetricsSink.none(), flatFloorY, null);
    }

    /** An unsaved match with a fixed seed and two factions, under {@link #GAMETEST_PLAY}. */
    public static MatchOptions gameTest(long seed, double flatFloorY) {
        return new MatchOptions(
                seed, 2, false, MatchSource.GAMETEST, null, MetricsSink.none(), flatFloorY, null, GAMETEST_PLAY);
    }

    public MatchOptions withPersistent(boolean persistent) {
        return new MatchOptions(seed, factions, persistent, source, faultPolicy, metrics, flatFloorY, map, play);
    }

    public MatchOptions withFaultPolicy(FaultPolicy faultPolicy) {
        return new MatchOptions(seed, factions, persistent, source, faultPolicy, metrics, flatFloorY, map, play);
    }

    public MatchOptions withMetrics(MetricsSink metrics) {
        return new MatchOptions(seed, factions, persistent, source, faultPolicy, metrics, flatFloorY, map, play);
    }

    public MatchOptions withSeed(long seed) {
        return new MatchOptions(seed, factions, persistent, source, faultPolicy, metrics, flatFloorY, map, play);
    }

    /** The match imports map package {@code map} into its space, its units standing at {@code floorY}. */
    public MatchOptions withMap(ResourceLocation map, double floorY) {
        return new MatchOptions(seed, factions, persistent, source, faultPolicy, metrics, floorY, map, play);
    }

    public MatchOptions withPlay(PlayRules play) {
        return new MatchOptions(seed, factions, persistent, source, faultPolicy, metrics, flatFloorY, map, play);
    }
}
