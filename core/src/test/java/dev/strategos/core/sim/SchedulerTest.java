package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchedulerTest {
    private final List<String> log = new ArrayList<>();

    @Test
    void runsAPhasesSystemsInRegistrationOrder() {
        Scheduler scheduler = new Scheduler();
        scheduler.register(system("b", Phase.MOVEMENT, Cadence.EVERY_TICK, false));
        scheduler.register(system("a", Phase.MOVEMENT, Cadence.EVERY_TICK, false));
        scheduler.register(system("c", Phase.MOVEMENT, Cadence.EVERY_TICK, false));

        scheduler.runPhase(Phase.MOVEMENT, TestStates.empty());

        assertThat(log).containsExactly("b", "a", "c");
        assertThat(scheduler.systemsOf(Phase.MOVEMENT)).hasSize(3);
    }

    @Test
    void runsThePhasesFromIngestToFinal() {
        Scheduler scheduler = new Scheduler();
        Phase[] phases = Phase.values();
        for (int i = phases.length - 1; i >= 0; i--) { // registered in reverse, run in phase order
            scheduler.register(system(phases[i].name(), phases[i], Cadence.EVERY_TICK, false));
        }
        MatchState state = TestStates.empty();
        for (Phase phase : Phase.values()) {
            scheduler.runPhase(phase, state);
        }
        assertThat(log)
                .containsExactly(
                        "INGEST", "COMMANDS", "AI", "UNIT_AI", "MOVEMENT", "COMBAT", "ECONOMY", "WORLD", "FINAL");
        assertThat(Phase.names()).containsExactlyElementsOf(log);
    }

    @Test
    void skipsSystemsWhoseCadenceDoesNotRunThisTick() {
        Scheduler scheduler = new Scheduler();
        scheduler.register(system("every", Phase.AI, Cadence.EVERY_TICK, false));
        scheduler.register(system("third", Phase.AI, new Cadence(3, 1), false));
        MatchState state = TestStates.empty();
        for (long tick = 1; tick <= 6; tick++) {
            state.setTick(tick);
            scheduler.runPhase(Phase.AI, state);
        }
        assertThat(log).containsExactly("every", "third", "every", "every", "every", "third", "every", "every");
    }

    @Test
    void rejectsASecondHeavySystemWithTheSamePhaseAndCadence() {
        Scheduler scheduler = new Scheduler();
        scheduler.register(system("heavy", Phase.AI, new Cadence(10, 0), true));
        scheduler.register(system("light", Phase.AI, new Cadence(10, 0), false));
        scheduler.register(system("other offset", Phase.AI, new Cadence(10, 5), true));
        scheduler.register(system("other phase", Phase.WORLD, new Cadence(10, 0), true));

        assertThatThrownBy(() -> scheduler.register(system("twin", Phase.AI, new Cadence(10, 0), true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AI");
        scheduler.register(system("every tick", Phase.AI, Cadence.EVERY_TICK, true));
        scheduler.register(system("every tick too", Phase.AI, Cadence.EVERY_TICK, true));
    }

    private MatchSystem system(String name, Phase phase, Cadence cadence, boolean heavy) {
        return new MatchSystem() {
            @Override
            public Phase phase() {
                return phase;
            }

            @Override
            public Cadence cadence() {
                return cadence;
            }

            @Override
            public boolean heavy() {
                return heavy;
            }

            @Override
            public void run(MatchState state) {
                log.add(name);
            }
        };
    }
}
