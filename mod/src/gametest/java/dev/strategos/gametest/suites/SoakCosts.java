package dev.strategos.gametest.suites;

import dev.strategos.core.sim.econ.EconomyTimes;
import org.jspecify.annotations.Nullable;

/**
 * What the first loop's soak adds (PERF-06.4), per sampled tick in nanoseconds: the block writer's {@code setBlock}
 * time, the fx sent for the match, and the commands phase of each tick that applied a placement; the placements
 * accepted and refused, the waves, the buildings at the end, one save of the match's blob in the window (budget G),
 * -1 if it has none, the economy's node index and dropsite query times in the window (PERF-07.4), and the battle's
 * combat systems' times (PERF-08).
 */
final class SoakCosts {
    private final long[] writer;
    private final long[] fx;
    private final long[] adapter;
    private final long[][] phases;
    private final java.util.List<String> phaseNames;
    private final long[] placement;
    private final long placed;
    private final long refused;
    private final int waves;
    private final int buildings;
    private final long saveNanos;
    private final EconomyTimes.Stats economy;
    private final @Nullable Combat combat;

    /**
     * PERF-08: per sampled tick in nanoseconds, the units' enemy searches (targeting), the centres' volleys, the shot
     * system and the damage system; and in the window the searches made, the shots fired, the hits dealt and the deaths.
     */
    record Combat(
            long[] targeting,
            long[] volleys,
            long[] shots,
            long[] damage,
            long searches,
            long shotsFired,
            long hits,
            long deaths) {}

    SoakCosts(
            long[] writer,
            long[] fx,
            long[] adapter,
            long[][] phases,
            java.util.List<String> phaseNames,
            long[] placement,
            long placed,
            long refused,
            int waves,
            int buildings,
            long saveNanos,
            EconomyTimes.Stats economy,
            @Nullable Combat combat) {
        this.writer = writer;
        this.fx = fx;
        this.adapter = adapter;
        this.phases = phases;
        this.phaseNames = phaseNames;
        this.placement = placement;
        this.placed = placed;
        this.refused = refused;
        this.waves = waves;
        this.buildings = buildings;
        this.saveNanos = saveNanos;
        this.economy = economy;
        this.combat = combat;
    }

    long[] writer() {
        return writer;
    }

    long[] fx() {
        return fx;
    }

    /** The core's phases each sampled tick, by {@link #phaseNames}. */
    long[][] phases() {
        return phases;
    }

    java.util.List<String> phaseNames() {
        return phaseNames;
    }

    /** The mod's part of budget A each sampled tick: applying the outputs and materialising bodies. */
    long[] adapter() {
        return adapter;
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

    /** PERF-08: the combat systems' times; null for the other scenes. */
    @Nullable Combat combat() {
        return combat;
    }
}
