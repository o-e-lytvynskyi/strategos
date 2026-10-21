package dev.strategos.core.diag;

import dev.strategos.core.shared.CoreApi;

/** One line of {@link PhaseTimers#stats()}, in nanoseconds. */
@CoreApi
public record PhaseStat(String name, int samples, long p50, long p95, long p99, long max) {}
