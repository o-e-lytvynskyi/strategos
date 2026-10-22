package dev.strategos.mod.server.tick;

import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.TickInputs;
import java.util.ArrayList;
import java.util.List;

/** Turns a match's inbox and its due terrain pages into the next tick's inputs. */
public final class InputCollector {
    private InputCollector() {}

    /** A copy of the inbox in arrival order, which it then clears; the core puts the commands in canonical order. */
    public static TickInputs collect(long tick, List<CommandEnvelope> inbox) {
        return collect(tick, inbox, List.of());
    }

    /** The pages first, then the inbox, which it clears; the core sorts both into canonical order. */
    public static TickInputs collect(long tick, List<CommandEnvelope> inbox, List<TerrainPage> pages) {
        List<InputEvent> events = new ArrayList<>(pages.size() + inbox.size());
        events.addAll(pages);
        events.addAll(inbox);
        inbox.clear();
        return new TickInputs(tick, events);
    }
}
