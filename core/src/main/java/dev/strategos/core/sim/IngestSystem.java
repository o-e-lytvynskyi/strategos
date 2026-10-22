package dev.strategos.core.sim;

import dev.strategos.core.sim.command.CommandSystem;
import dev.strategos.core.sim.journal.JournalWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * P0: puts the tick's inputs in canonical order (terrain pages by page, Z then X, then commands), records them in the
 * journal and routes each to its one handler: a page is published, a command queued.
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
        return List.of(CommandEnvelope.class, TerrainPage.class);
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
        for (InputEvent event : events) {
            switch (event) {
                case TerrainPage page -> state.publish(page);
                case CommandEnvelope envelope -> commands.enqueue(envelope);
            }
        }
    }

    private static int canonical(InputEvent a, InputEvent b) {
        if (a instanceof TerrainPage pa && b instanceof TerrainPage pb) {
            int byZ = Integer.compare(pa.coord().z(), pb.coord().z());
            return byZ != 0 ? byZ : Integer.compare(pa.coord().x(), pb.coord().x());
        }
        if (a instanceof CommandEnvelope ca && b instanceof CommandEnvelope cb) {
            return CommandEnvelope.CANONICAL.compare(ca, cb);
        }
        return a instanceof TerrainPage ? -1 : 1;
    }
}
