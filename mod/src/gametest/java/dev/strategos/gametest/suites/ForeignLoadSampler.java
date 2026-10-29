package dev.strategos.gametest.suites;

import dev.strategos.core.testkit.CpuTimes;
import dev.strategos.core.testkit.ForeignLoad.Reading;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Reads {@link CpuCounters} once a second on a daemon thread for the whole perf batch, so every window's load comes
 * from one-second intervals and the time before the first window (the preflight) is on record too. The first
 * failed read is kept as the reason and ends the sampling: later windows are then unavailable, never zero.
 */
public final class ForeignLoadSampler {
    private static final long INTERVAL_MILLIS = 1_000;
    /** About ten hours, far over the longest batch; reaching it ends the sampling as a failure. */
    private static final int CAPACITY = 36_000;

    private static final Object LOCK = new Object();
    private static final List<Reading> READINGS = new ArrayList<>();
    private static @Nullable String failure;
    private static boolean started;

    private ForeignLoadSampler() {}

    /** Starts the sampler once per JVM; with the self-test override set there are no real reads. */
    public static void start() {
        synchronized (LOCK) {
            if (started
                    || !System.getProperty("strategos.perf.fakeForeignLoad", "").isBlank()) {
                return;
            }
            started = true;
        }
        Thread thread = new Thread(ForeignLoadSampler::run, "strategos-foreign-load");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        while (true) {
            try {
                Reading reading = now();
                synchronized (LOCK) {
                    if (READINGS.size() >= CAPACITY) {
                        failure = "foreign-load sampler capacity reached";
                        return;
                    }
                    READINGS.add(reading);
                }
                Thread.sleep(INTERVAL_MILLIS);
            } catch (CpuCounters.Unavailable e) {
                synchronized (LOCK) {
                    failure = e.getMessage();
                }
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** A fresh reading stamped with {@link System#nanoTime()}. */
    static Reading now() {
        long nanos = System.nanoTime();
        CpuTimes times = CpuCounters.read();
        return new Reading(nanos, times);
    }

    /** The readings stamped within [from, to] (nanoTime instants), in order. */
    static List<Reading> between(long from, long to) {
        synchronized (LOCK) {
            List<Reading> out = new ArrayList<>();
            for (Reading r : READINGS) {
                if (r.nanos() >= from && r.nanos() <= to) {
                    out.add(r);
                }
            }
            return out;
        }
    }

    /** Why sampling stopped, or null while it runs (or never started). */
    static @Nullable String failure() {
        synchronized (LOCK) {
            return failure;
        }
    }
}
