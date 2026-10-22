package dev.strategos.core.sim;

import dev.strategos.core.port.Clock;
import dev.strategos.core.port.JobExecutor;
import dev.strategos.core.port.JournalSink;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.shared.CoreApi;

/** What the core reaches outside itself: metrics, the clock, the executor of path jobs and the journal's sink. */
@CoreApi
public record CorePorts(MetricsSink metrics, Clock clock, JobExecutor jobs, JournalSink journal) {
    /** Inline jobs and the in-memory journal. */
    public CorePorts(MetricsSink metrics, Clock clock) {
        this(metrics, clock, JobExecutor.inline(), JournalSink.MEMORY);
    }

    /** No metrics, inline jobs, the in-memory journal. */
    public static CorePorts of(Clock clock) {
        return new CorePorts(MetricsSink.none(), clock);
    }
}
