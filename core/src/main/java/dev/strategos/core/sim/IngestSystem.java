package dev.strategos.core.sim;

import dev.strategos.core.sim.command.CommandSystem;
import dev.strategos.core.sim.journal.JournalWriter;
import java.util.ArrayList;
import java.util.List;

/** P0: puts the tick's inputs in canonical order, records them in the journal and routes each to its one handler. */
final class IngestSystem implements MatchSystem {
    private final CommandSystem commands;
    private final JournalWriter journal;

    IngestSystem(CommandSystem commands, JournalWriter journal) {
        this.commands = commands;
        this.journal = journal;
    }

    /** Every {@link InputEvent} type this system routes; a test checks it against the sealed list. */
    static List<Class<? extends InputEvent>> handledTypes() {
        return List.of(CommandEnvelope.class);
    }

    @Override
    public Phase phase() {
        return Phase.INGEST;
    }

    @Override
    public void run(MatchState state) {
        List<InputEvent> events = new ArrayList<>(state.input().events());
        events.sort((a, b) -> CommandEnvelope.CANONICAL.compare(envelope(a), envelope(b))); // stable
        journal.record(new TickInputs(state.tick(), events));
        for (InputEvent event : events) {
            switch (event) {
                case CommandEnvelope envelope -> commands.enqueue(envelope);
            }
        }
    }

    private static CommandEnvelope envelope(InputEvent event) {
        return switch (event) {
            case CommandEnvelope envelope -> envelope;
        };
    }
}
