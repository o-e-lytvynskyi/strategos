package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import java.util.List;

/** What a step produced: the poses of the bodies that moved, and the events for the mod. */
@CoreApi
public record TickOutputs(long tick, BodyFrame bodies, List<OutputEvent> events) {
    public TickOutputs {
        events = List.copyOf(events);
    }
}
