package dev.strategos.core.testkit;

import dev.strategos.core.port.MetricsSink;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Records the phases the core announces, per tick: the recorded trace of CORE-02.1. */
public final class RecordingMetrics implements MetricsSink {
    private final Map<Long, List<Integer>> phases = new LinkedHashMap<>();

    @Override
    public void onPhase(long tick, int phaseOrdinal) {
        phases.computeIfAbsent(tick, t -> new ArrayList<>()).add(phaseOrdinal);
    }

    @Override
    public void onTick(long tick, long[] phaseNanos) {}

    /** The phases announced in {@code tick}, in order; empty for a tick that never ran. */
    public List<Integer> phasesOf(long tick) {
        return List.copyOf(phases.getOrDefault(tick, List.of()));
    }
}
