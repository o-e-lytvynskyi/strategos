package dev.strategos.mod.server.tick;

import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.TickInputs;
import java.util.ArrayList;
import java.util.List;

/** Turns a match's inbox into the next tick's inputs. */
public final class InputCollector {
    private InputCollector() {}

    /** A copy of the inbox in arrival order, which it then clears; the core puts the commands in canonical order. */
    public static TickInputs collect(long tick, List<CommandEnvelope> inbox) {
        List<InputEvent> events = new ArrayList<>(inbox);
        inbox.clear();
        return new TickInputs(tick, events);
    }
}
