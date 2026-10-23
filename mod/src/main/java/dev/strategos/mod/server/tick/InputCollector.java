package dev.strategos.mod.server.tick;

import dev.strategos.core.sim.AgentUpdate;
import dev.strategos.core.sim.CommandEnvelope;
import dev.strategos.core.sim.ContentSwap;
import dev.strategos.core.sim.ExternalDamage;
import dev.strategos.core.sim.InputEvent;
import dev.strategos.core.sim.JobDelivery;
import dev.strategos.core.sim.LeaderUpdate;
import dev.strategos.core.sim.TerrainPage;
import dev.strategos.core.sim.TickInputs;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Turns a match's inbox, its due terrain pages, its agents, damage and finished path jobs into the next tick's inputs. */
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
        return collect(tick, inbox, pages, agents, damage, null);
    }

    /** As above, and last the path jobs an {@code ADAPTIVE} match found finished, if any (PATH-16). */
    public static TickInputs collect(
            long tick,
            List<CommandEnvelope> inbox,
            List<TerrainPage> pages,
            @Nullable AgentUpdate agents,
            List<ExternalDamage> damage,
            @Nullable JobDelivery delivery) {
        return collect(tick, inbox, pages, agents, damage, delivery, null);
    }

    /** As above, and a new template set to swap in, if a reload gave one (DATA-03). */
    public static TickInputs collect(
            long tick,
            List<CommandEnvelope> inbox,
            List<TerrainPage> pages,
            @Nullable AgentUpdate agents,
            List<ExternalDamage> damage,
            @Nullable JobDelivery delivery,
            @Nullable ContentSwap swap) {
        return collect(tick, inbox, pages, agents, List.of(), damage, delivery, swap);
    }

    /** As above, with where the match's commanders in first person stand, who lead their followers (CAM-07). */
    public static TickInputs collect(
            long tick,
            List<CommandEnvelope> inbox,
            List<TerrainPage> pages,
            @Nullable AgentUpdate agents,
            List<LeaderUpdate> leaders,
            List<ExternalDamage> damage,
            @Nullable JobDelivery delivery,
            @Nullable ContentSwap swap) {
        List<InputEvent> events = new ArrayList<>(pages.size() + inbox.size() + damage.size() + leaders.size() + 2);
        events.addAll(pages);
        events.addAll(inbox);
        if (agents != null) {
            events.add(agents);
        }
        events.addAll(leaders);
        events.addAll(damage);
        if (delivery != null) {
            events.add(delivery);
        }
        if (swap != null) {
            events.add(swap);
        }
        inbox.clear();
        damage.clear();
        return new TickInputs(tick, events);
    }
}
