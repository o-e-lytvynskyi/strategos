package dev.strategos.gametest.suites;

import dev.strategos.core.sim.econ.EconomyTimes;

/**
 * What the first loop's soak adds (PERF-06.4), per sampled tick in nanoseconds: the block writer's {@code setBlock}
 * time, the fx sent for the match, and the commands phase of each tick that applied a placement; the placements
 * accepted and refused, the waves, the buildings at the end, one save of the match's blob in the window (budget G),
 * -1 if it has none, and the economy's node index and dropsite query times in the window (PERF-07.4).
 */
final class SoakCosts {
    private final long[] writer;
    private final long[] fx;
    private final long[] placement;
    private final long placed;
    private final long refused;
    private final int waves;
    private final int buildings;
    private final long saveNanos;
    private final EconomyTimes.Stats economy;

    SoakCosts(
            long[] writer,
            long[] fx,
            long[] placement,
            long placed,
            long refused,
            int waves,
            int buildings,
            long saveNanos,
            EconomyTimes.Stats economy) {
        this.writer = writer;
        this.fx = fx;
        this.placement = placement;
        this.placed = placed;
        this.refused = refused;
        this.waves = waves;
        this.buildings = buildings;
        this.saveNanos = saveNanos;
        this.economy = economy;
    }

    long[] writer() {
        return writer;
    }

    long[] fx() {
        return fx;
    }

    long[] placement() {
        return placement;
    }

    long placed() {
        return placed;
    }

    long refused() {
        return refused;
    }

    int waves() {
        return waves;
    }

    int buildings() {
        return buildings;
    }

    long saveNanos() {
        return saveNanos;
    }

    /** PERF-07.4: the node index's and the dropsite queries' counts and times in the window. */
    EconomyTimes.Stats economy() {
        return economy;
    }
}
