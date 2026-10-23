package dev.strategos.core.sim;

import dev.strategos.core.sim.command.CommandSystem;
import dev.strategos.core.sim.journal.JournalWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * P0: puts the tick's inputs in canonical order (terrain pages by page, Z then X, then commands, then the agents, then
 * damage to bodies in the order it came, then path deliveries, then a template set), records them in the journal and
 * routes each to its one handler: the pages are published together, a command is queued, the agents are the tick's for
 * avoidance, a delivery lists the finished jobs the path service applies this tick, a new template set is swapped in
 * before any command applies; damage is only recorded until CMB-01 applies it.
 */
final class IngestSystem implements MatchSystem {
    private final CommandSystem commands;
    private final JournalWriter journal;

    IngestSystem(CommandSystem commands, JournalWriter journal) {
        this.commands = commands;
        this.journal = journal;
    }

    /** Every {@link InputEvent} type this system routes; a test checks it against the sealed list. */
    static List<Class<? extends InputEvent>> handledTypes() {
        return List.of(
                CommandEnvelope.class,
                TerrainPage.class,
                AgentUpdate.class,
                ExternalDamage.class,
                JobDelivery.class,
                ContentSwap.class);
    }

    @Override
    public Phase phase() {
        return Phase.INGEST;
    }

    @Override
    public void run(MatchState state) {
        List<InputEvent> events = new ArrayList<>(state.input().events());
        events.sort(IngestSystem::canonical); // stable
        journal.record(new TickInputs(state.tick(), events));
        List<TerrainPage> pages = new ArrayList<>();
        for (InputEvent event : events) {
            switch (event) {
                case TerrainPage page -> pages.add(page);
                case CommandEnvelope envelope -> commands.enqueue(envelope);
                case AgentUpdate update -> state.addAgents(update.agents());
                case ExternalDamage damage -> {} // journaled above; CMB-01 applies it
                case JobDelivery delivery -> state.addDelivery(delivery);
                case ContentSwap swap -> state.swapTemplates(swap.set());
            }
        }
        state.publishAll(pages);
    }

    private static int canonical(InputEvent a, InputEvent b) {
        if (a instanceof TerrainPage pa && b instanceof TerrainPage pb) {
            int byZ = Integer.compare(pa.coord().z(), pb.coord().z());
            return byZ != 0 ? byZ : Integer.compare(pa.coord().x(), pb.coord().x());
        }
        if (a instanceof CommandEnvelope ca && b instanceof CommandEnvelope cb) {
            return CommandEnvelope.CANONICAL.compare(ca, cb);
        }
        return Integer.compare(rank(a), rank(b)); // two of the other kinds keep their order
    }

    private static int rank(InputEvent event) {
        return switch (event) {
            case TerrainPage page -> 0;
            case CommandEnvelope envelope -> 1;
            case AgentUpdate update -> 2;
            case ExternalDamage damage -> 3;
            case JobDelivery delivery -> 4;
            case ContentSwap swap -> 5;
        };
    }
}
