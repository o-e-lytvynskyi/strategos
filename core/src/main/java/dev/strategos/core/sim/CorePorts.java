package dev.strategos.core.sim;

import dev.strategos.core.port.Clock;
import dev.strategos.core.port.MetricsSink;
import dev.strategos.core.shared.CoreApi;

/** What the core reaches outside itself; M1 adds jobs and the journal sink. */
@CoreApi
public record CorePorts(MetricsSink metrics, Clock clock) {
    /** No metrics. */
    public static CorePorts of(Clock clock) {
        return new CorePorts(MetricsSink.none(), clock);
    }
}
