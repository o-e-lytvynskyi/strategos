package dev.strategos.core.diag;

import dev.strategos.core.port.Clock;
import dev.strategos.core.shared.CoreApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Times every phase of a match's step, the step itself and the mod's adapter work, over a window of ticks. It reads time
 * only through {@link Clock}. Per tick: {@link #beginTick()}, then {@link #beginPhase}/{@link #endPhase} per phase, then
 * {@link #endTick()}; the mod then calls {@link #recordAdapter} once with the time it spent applying the outputs.
 */
@CoreApi
public final class PhaseTimers {
    private final List<String> phaseNames;
    private final Clock clock;
    private final RingHistogram[] phases;
    private final RingHistogram step;
    private final RingHistogram adapter;
    private final RingHistogram total;
    private final long[] current;
    private final long[] last;

    private long tickStart;
    private long phaseStart;
    private long lastStep;
    private long lastAdapter;
    private boolean adapterOpen;

    /** {@code window} is the number of ticks the statistics cover: 600 in the mod. */
    public PhaseTimers(List<String> phaseNames, Clock clock, int window) {
        this.phaseNames = List.copyOf(phaseNames);
        this.clock = clock;
        this.phases = new RingHistogram[phaseNames.size()];
        for (int i = 0; i < phases.length; i++) {
            phases[i] = new RingHistogram(window);
        }
        this.step = new RingHistogram(window);
        this.adapter = new RingHistogram(window);
        this.total = new RingHistogram(window);
        this.current = new long[phaseNames.size()];
        this.last = new long[phaseNames.size()];
    }

    public void beginTick() {
        adapterOpen = false;
        Arrays.fill(current, 0);
        tickStart = clock.nanoTime();
    }

    public void beginPhase(int ordinal) {
        phaseStart = clock.nanoTime();
    }

    public void endPhase(int ordinal) {
        current[ordinal] += clock.nanoTime() - phaseStart;
    }

    /** Ends the step and records it; returns the step's nanoseconds. */
    public long endTick() {
        lastStep = clock.nanoTime() - tickStart;
        System.arraycopy(current, 0, last, 0, current.length);
        for (int i = 0; i < phases.length; i++) {
            phases[i].record(current[i]);
        }
        step.record(lastStep);
        lastAdapter = 0;
        adapterOpen = true;
        return lastStep;
    }

    /** The mod's time applying this tick's outputs and materialising bodies, part of budget A: once per tick. */
    public void recordAdapter(long nanos) {
        if (!adapterOpen) {
            throw new IllegalStateException("adapter time needs a step that ended and has none recorded yet");
        }
        adapterOpen = false;
        lastAdapter = nanos;
        adapter.record(nanos);
        total.record(lastStep + nanos);
    }

    public long lastStepNanos() {
        return lastStep;
    }

    public long lastAdapterNanos() {
        return lastAdapter;
    }

    /** The last step plus its adapter time, once recorded. */
    public long lastTotalNanos() {
        return lastStep + lastAdapter;
    }

    /** The tick in progress, measured so far: the array is reused every tick, so copy it to keep it. */
    public long[] currentPhaseNanos() {
        return current;
    }

    /** A copy of the last step's time per phase. */
    public long[] lastPhaseNanos() {
        return last.clone();
    }

    /** One line per phase, then "step", "adapter" and "total"; adapter and total count the ticks that recorded it. */
    public List<PhaseStat> stats() {
        List<PhaseStat> stats = new ArrayList<>(phases.length + 3);
        for (int i = 0; i < phases.length; i++) {
            stats.add(stat(phaseNames.get(i), phases[i]));
        }
        stats.add(stat("step", step));
        stats.add(stat("adapter", adapter));
        stats.add(stat("total", total));
        return List.copyOf(stats);
    }

    private static PhaseStat stat(String name, RingHistogram h) {
        return new PhaseStat(name, h.count(), h.percentile(0.5), h.percentile(0.95), h.percentile(0.99), h.max());
    }
}
