package dev.strategos.core.sim;

import dev.strategos.core.sim.command.CommandSystem;
import java.util.ArrayList;
import java.util.List;

/**
 * P0: puts the tick's inputs in canonical order and routes each to its one handler. T6a also records the canonical
 * inputs in the journal.
 */
final class IngestSystem implements MatchSystem {
    private final CommandSystem commands;

    IngestSystem(CommandSystem commands) {
        this.commands = commands;
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
