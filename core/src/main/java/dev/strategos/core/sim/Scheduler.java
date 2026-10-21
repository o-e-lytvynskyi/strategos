package dev.strategos.core.sim;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/** Runs a match's systems phase by phase, in registration order within a phase. */
public final class Scheduler {
    private final EnumMap<Phase, List<MatchSystem>> systems = new EnumMap<>(Phase.class);

    public Scheduler() {
        for (Phase phase : Phase.values()) {
            systems.put(phase, new ArrayList<>());
        }
    }

    /** Rejects a second heavy system with the same phase and cadence, when that cadence skips ticks. */
    public void register(MatchSystem system) {
        List<MatchSystem> phase = listOf(system.phase());
        if (system.heavy() && system.cadence().period() > 1) {
            for (MatchSystem other : phase) {
                if (other.heavy() && other.cadence().equals(system.cadence())) {
                    throw new IllegalArgumentException("heavy systems "
                            + other.getClass().getSimpleName() + " and "
                            + system.getClass().getSimpleName() + " share phase " + system.phase() + " and cadence "
                            + system.cadence() + ": give one another offset");
                }
            }
        }
        phase.add(system);
    }

    /** Runs the phase's systems in registration order, skipping those whose cadence does not run this tick. */
    public void runPhase(Phase phase, MatchState state) {
        List<MatchSystem> list = listOf(phase);
        for (int i = 0; i < list.size(); i++) {
            MatchSystem system = list.get(i);
            if (system.cadence().runsAt(state.tick())) {
                system.run(state);
            }
        }
    }

    public List<MatchSystem> systemsOf(Phase phase) {
        return List.copyOf(listOf(phase));
    }

    private List<MatchSystem> listOf(Phase phase) {
        return Objects.requireNonNull(systems.get(phase), "every phase has a list from the constructor");
    }
}
