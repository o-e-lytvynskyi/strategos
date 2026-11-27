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
    private final long[] trackers;
    private final @Nullable Territory territory;
    private final @Nullable Depth depth;

    /**
     * PERF-17: per sampled tick in nanoseconds, the formation system's run, the wild animals' thinking and the ability
     * system's run; and the scene's own account: the share of the formations' members that stood formed, the marches,
     * charges and researches it ordered, and each faction's phase at the end of the window.
     */
    record Depth(
            long[] formation,
            long[] fauna,
            long[] ability,
            double formed,
            long marches,
            long charges,
            long researches,
            String[] phases) {}

    /**
     * PERF-16: per sampled tick in nanoseconds, the territory system's run, the tick thread's wait on its recompute job,
     * the garrison system's and the capture system's runs; and the territory grids delivered that tick.
     */
    static final class Territory {
        private final long[] territory;
        private final long[] join;
        private final long[] garrison;
        private final long[] capture;
        private final long[] recomputes;

        Territory(long[] territory, long[] join, long[] garrison, long[] capture, long[] recomputes) {
            this.territory = territory;
            this.join = join;
            this.garrison = garrison;
            this.capture = capture;
            this.recomputes = recomputes;
        }

        long[] territory() {
            return territory;
        }

        long[] join() {
            return join;
        }

        long[] garrison() {
            return garrison;
        }

        long[] capture() {
            return capture;
        }

        long[] recomputes() {
            return recomputes;
        }
    }

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
            @Nullable Combat combat,
            long[] trackers,
            @Nullable Territory territory,
            @Nullable Depth depth) {
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
        this.trackers = trackers;
        this.territory = territory;
        this.depth = depth;
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

    /**
     * PERF-09: the trackers' refresh each sampled tick in nanoseconds, which runs one tick in five; empty for the other
     * scenes.
     */
    long[] trackers() {
        return trackers;
    }

    /** PERF-16: the territory and garrison systems' times; null for the other scenes. */
    @Nullable Territory territory() {
        return territory;
    }

    /** PERF-17: the formation, fauna and ability costs; null for the other scenes. */
    @Nullable Depth depth() {
        return depth;
    }
}
