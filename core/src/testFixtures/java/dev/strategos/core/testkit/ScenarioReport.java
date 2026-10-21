package dev.strategos.core.testkit;

import org.jspecify.annotations.Nullable;

/** A scenario run: whether it passed, the tick it stopped at, that tick's state hash, and the failure if any. */
public record ScenarioReport(
        String id,
        boolean passed,
        long ticks,
        long finalHash,
        @Nullable String failure) {}
