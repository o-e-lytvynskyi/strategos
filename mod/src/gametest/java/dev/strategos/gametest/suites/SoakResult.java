package dev.strategos.gametest.suites;

import static dev.strategos.gametest.suites.Units.core;

import dev.strategos.core.sim.Stuck;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a soak saw. Samples are per soak tick after the warm-up, in nanoseconds: {@code core} the core's step with our
 * appliers, {@code tick} the server's tick, {@code between} our handlers' time before it, {@code paths} the main
 * thread's path work; {@code baseline} the arena's ticks without units. {@code viewerBytes} each viewer's bytes over
 * the sampled window. {@code stuck} counts units by their longest run without progress, by the stuck ladder's rungs
 * (under 40 ticks, 40, 100, 200, 400 and 600 or more).
 */
final class SoakResult {
    private final CrowdSoak.Config config;
    private final long seed;
    private final long[] core;
    private final long[] tick;
    private final long[] between;
    private final long[] paths;
    private final long[] baseline;
    private final long[] viewerBytes;
    private final double viewerUnits;
    private final int viewerUnitsMost;
    private final long groupOrders;
    private final long unitOrders;
    private final long refused;
    private final long[] rungs;
    private final int[] stuck;
    private final int mostStuck;
    private final String worst;
    private final long navBytes;
    private final int units;
    private final @Nullable Path journal;
    private final long[] fights;
    private final @Nullable SoakCosts costs;

    SoakResult(
            CrowdSoak.Config config,
            long seed,
            long[] core,
            long[] tick,
            long[] between,
            long[] paths,
            long[] baseline,
            long[] viewerBytes,
            double viewerUnits,
            int viewerUnitsMost,
            long groupOrders,
            long unitOrders,
            long refused,
            long[] rungs,
            int[] stuck,
            int mostStuck,
            String worst,
            long navBytes,
            int units,
            @Nullable Path journal,
            long[] fights,
            @Nullable SoakCosts costs) {
        this.config = config;
        this.seed = seed;
        this.core = core;
        this.tick = tick;
        this.between = between;
        this.paths = paths;
        this.baseline = baseline;
        this.viewerBytes = viewerBytes;
        this.viewerUnits = viewerUnits;
        this.viewerUnitsMost = viewerUnitsMost;
        this.groupOrders = groupOrders;
        this.unitOrders = unitOrders;
        this.refused = refused;
        this.rungs = rungs;
        this.stuck = stuck;
        this.mostStuck = mostStuck;
        this.worst = worst;
        this.navBytes = navBytes;
        this.units = units;
        this.journal = journal;
        this.fights = fights;
        this.costs = costs;
    }

    /** The first loop's costs; null for the other soaks. */
    @Nullable SoakCosts costs() {
        return costs;
    }

    /** The melee soak's waves sent, hits and deaths; zeros for the crowd's. */
    long[] fights() {
        return fights;
    }

    CrowdSoak.Config config() {
        return config;
    }

    long seed() {
        return seed;
    }

    long[] core() {
        return core;
    }

    long[] tick() {
        return tick;
    }

    long[] between() {
        return between;
    }

    long[] paths() {
        return paths;
    }

    long[] baseline() {
        return baseline;
    }

    long[] viewerBytes() {
        return viewerBytes;
    }

    /** The bodies a viewer was paired with, on average over the window's seconds. */
    double viewerUnits() {
        return viewerUnits;
    }

    /** The most bodies a viewer was paired with at once. */
    int viewerUnitsMost() {
        return viewerUnitsMost;
    }

    long groupOrders() {
        return groupOrders;
    }

    long unitOrders() {
        return unitOrders;
    }

    long[] rungs() {
        return rungs;
    }

    int[] stuck() {
        return stuck;
    }

    int mostStuck() {
        return mostStuck;
    }

    String worst() {
        return worst;
    }

    long navBytes() {
        return navBytes;
    }

    int units() {
        return units;
    }

    @Nullable Path journal() {
        return journal;
    }

    long failedOrders() {
        return refused + rungs[Stuck.GIVE_UP];
    }

    long nudges() {
        return rungs[4];
    }

    /** The soak's own failures (PERF-05.2), budgets aside. */
    List<String> failures() {
        List<String> failures = new ArrayList<>();
        if (failedOrders() > 0) {
            failures.add(
                    failedOrders() + " failed orders (" + refused + " refused, " + rungs[Stuck.GIVE_UP] + " given up)");
        }
        if (nudges() * 1_000 > unitOrders) {
            failures.add(nudges() + " nudges for " + unitOrders + " unit orders, over 1 per 1,000");
        }
        if (mostStuck > CrowdSoak.MOST_STUCK) {
            failures.add(
                    "a unit " + mostStuck + " ticks without progress, over " + CrowdSoak.MOST_STUCK + ": " + worst);
        }
        return failures;
    }
}
