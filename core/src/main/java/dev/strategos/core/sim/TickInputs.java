package dev.strategos.core.sim;

import dev.strategos.core.shared.CoreApi;
import java.util.List;

/** Everything that enters a match in one tick. */
@CoreApi
public record TickInputs(long tick, List<InputEvent> events) {
    public TickInputs {
        events = List.copyOf(events);
    }

    public static TickInputs empty(long tick) {
        return new TickInputs(tick, List.of());
    }
}
