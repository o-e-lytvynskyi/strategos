package dev.strategos.mod.server.tick;

import dev.strategos.core.sim.AgentUpdate;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.TickInputs;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Turns a match's inbox and its due terrain pages into the next tick's inputs. */
public final class InputCollector {
    private InputCollector() {}

    /** A copy of the inbox in arrival order, which it then clears; the core puts the commands in canonical order. */
    public static TickInputs collect(long tick, List<CommandEnvelope> inbox) {
        return collect(tick, inbox, List.of());
    }

    /** The pages first, then the inbox, which it clears; the core sorts both into canonical order. */
    public static TickInputs collect(long tick, List<CommandEnvelope> inbox, List<TerrainPage> pages) {
        return collect(tick, inbox, pages, new ArrayList<>());
    }

    /** As above, and then the damage bodies took since the last step, in the order it came, which it also clears. */
    public static TickInputs collect(
            long tick, List<CommandEnvelope> inbox, List<TerrainPage> pages, List<ExternalDamage> damage) {
        return collect(tick, inbox, pages, null, damage);
    }

    /** As above, with the tick's agents, if any, between the commands and the damage. */
    public static TickInputs collect(
            long tick,
            List<CommandEnvelope> inbox,
            List<TerrainPage> pages,
            @Nullable AgentUpdate agents,
            List<ExternalDamage> damage) {
        List<InputEvent> events = new ArrayList<>(pages.size() + inbox.size() + damage.size() + 1);
        events.addAll(pages);
        events.addAll(inbox);
        if (agents != null) {
            events.add(agents);
        }
        events.addAll(damage);
        inbox.clear();
        damage.clear();
        return new TickInputs(tick, events);
    }
}
