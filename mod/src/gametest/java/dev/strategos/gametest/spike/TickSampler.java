package dev.strategos.gametest.spike;

import dev.strategos.core.testkit.Percentiles;
import java.util.Arrays;
import net.minecraft.server.MinecraftServer;

/**
 * One configuration's measured ticks: the server's tick time (written just before {@code ServerTickEvent.Post},
 * MinecraftServer.java:954–958), and for production bodies the core step and the adapter.
 */
final class TickSampler {
    private final long[] tick;
    private final long[] step;
    private final long[] adapter;
    private int count;

    TickSampler(int capacity) {
        tick = new long[capacity];
        step = new long[capacity];
        adapter = new long[capacity];
    }

    /** From {@code ServerTickEvent.Post}; {@code stepNanos} and {@code adapterNanos} are -1 without a match. */
    void record(MinecraftServer server, long stepNanos, long adapterNanos) {
        tick[count] = server.getTickTimesNanos()[server.getTickCount() % 100];
        step[count] = stepNanos;
        adapter[count] = adapterNanos;
        count++;
    }

    int count() {
        return count;
    }

    boolean full() {
        return count == tick.length;
    }

    double tickMs(double q) {
        return ms(Percentiles.percentile(Arrays.copyOf(tick, count), q));
    }

    double tickMaxMs() {
        return ms(Arrays.stream(tick, 0, count).max().orElse(0));
    }

    /** NaN when the configuration has no match. */
    double stepP50Ms() {
        return step[0] < 0 ? Double.NaN : ms(Percentiles.percentile(Arrays.copyOf(step, count), 0.50));
    }

    double adapterP50Ms() {
        return adapter[0] < 0 ? Double.NaN : ms(Percentiles.percentile(Arrays.copyOf(adapter, count), 0.50));
    }

    private static double ms(long nanos) {
        return nanos / 1e6;
    }
}
