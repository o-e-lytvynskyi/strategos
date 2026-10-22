package dev.strategos.mod.server.jobs;

import dev.strategos.core.port.JobExecutor;

/**
 * How many path job threads a server runs (ADR-0015, spec §2.5): the {@code pathThreads} setting, or with -1 one from
 * the cores, {@code clamp(cores / 3, 1, 4)} on an integrated server, which leaves cores to the client, and {@code
 * clamp(cores / 2 - 1, 1, 4)} on a dedicated one; 0 runs every job on the server thread at its join.
 */
public final class PathThreads {
    /** The setting that picks from the cores. */
    public static final int AUTOMATIC = -1;

    /** The most threads a pool runs. */
    public static final int MAX = 4;

    private PathThreads() {}

    public static int count(int setting, int cores, boolean dedicated) {
        if (setting < AUTOMATIC || setting > MAX) {
            throw new IllegalArgumentException("pathThreads " + setting + " outside -1.." + MAX);
        }
        if (setting != AUTOMATIC) {
            return setting;
        }
        return clamp(dedicated ? cores / 2 - 1 : cores / 3);
    }

    /** The executor for {@code threads}: inline for 0, a pool otherwise. */
    public static JobExecutor executor(int threads) {
        return threads == 0 ? new InlineJobExecutor() : new PooledJobExecutor(threads);
    }

    private static int clamp(int n) {
        return Math.max(1, Math.min(MAX, n));
    }
}
