package dev.strategos.devtools.demo;

import dev.strategos.core.protocol.RejectReason;
import dev.strategos.core.sim.CommandResult;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/** The refusals a chapter's match sent back, by reason, read from its outputs; a chapter makes one and waits on it. */
final class Refusals {
    private final List<RejectReason> reasons = new CopyOnWriteArrayList<>();

    /** Starts listening to the stage's match. */
    void attach(Stage st) {
        st.listen(out -> out.events().forEach(e -> {
            if (e instanceof CommandResult r && r.rejected() > 0 && r.firstReason() != null) {
                reasons.add(r.firstReason());
            }
        }));
    }

    int count() {
        return reasons.size();
    }

    /** The reason of the latest refusal, or null before one. */
    @Nullable RejectReason last() {
        return reasons.isEmpty() ? null : reasons.getLast();
    }
}
