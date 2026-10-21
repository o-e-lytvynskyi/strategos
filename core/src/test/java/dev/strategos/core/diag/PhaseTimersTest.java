package dev.strategos.core.diag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.port.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhaseTimersTest {
    /** A clock the test advances by hand; the test kit's ManualClock arrives with T5b. */
    private long now;

    private final Clock clock = () -> now;

    @Test
    void phaseDurationsLandInTheirSlots() {
        PhaseTimers timers = new PhaseTimers(List.of("ingest", "move", "final"), clock, 600);
        timers.beginTick();
        now += 2; // between beginTick and the first phase: part of the step, in no phase
        timers.beginPhase(0);
        now += 10;
        timers.endPhase(0);
        timers.beginPhase(1);
        now += 30;
        timers.endPhase(1);
        timers.beginPhase(2);
        now += 5;
        timers.endPhase(2);

        assertThat(timers.endTick()).isEqualTo(47);
        assertThat(timers.lastPhaseNanos()).containsExactly(10, 30, 5);
        assertThat(timers.lastStepNanos()).isEqualTo(47);
    }

    @Test
    void nextPhaseEndsOnePhaseAndBeginsTheNextAtOneReading() {
        var reads = new Object() {
            int count;
        };
        Clock counting = () -> {
            reads.count++;
            return now;
        };
        PhaseTimers timers = new PhaseTimers(List.of("ingest", "move", "final"), counting, 600);
        timers.beginTick();
        timers.beginPhase(0);
        now += 10;
        timers.nextPhase(0, 1);
        now += 30;
        timers.nextPhase(1, 2);
        now += 5;
        timers.endPhase(2);

        assertThat(timers.endTick()).isEqualTo(45);
        assertThat(timers.lastPhaseNanos()).containsExactly(10, 30, 5);
        assertThat(reads.count)
                .as("one reading per phase boundary, plus the tick's two")
                .isEqualTo(6);
    }

    @Test
    void adapterTimeAddsToTheTotal() {
        PhaseTimers timers = new PhaseTimers(List.of("ingest"), clock, 600);
        timers.beginTick();
        timers.beginPhase(0);
        now += 40;
        timers.endPhase(0);
        timers.endTick();
        timers.recordAdapter(15);

        assertThat(timers.lastAdapterNanos()).isEqualTo(15);
        assertThat(timers.lastTotalNanos()).isEqualTo(55);
        assertThatThrownBy(() -> timers.recordAdapter(1)).as("once per tick").isInstanceOf(IllegalStateException.class);
    }

    @Test
    void statsListThePhasesThenStepAdapterAndTotal() {
        PhaseTimers timers = new PhaseTimers(List.of("ingest", "move"), clock, 600);
        for (int tick = 1; tick <= 3; tick++) {
            timers.beginTick();
            timers.beginPhase(0);
            now += tick;
            timers.endPhase(0);
            timers.beginPhase(1);
            now += 10L * tick;
            timers.endPhase(1);
            timers.endTick();
            timers.recordAdapter(100);
        }

        List<PhaseStat> stats = timers.stats();
        assertThat(stats).extracting(PhaseStat::name).containsExactly("ingest", "move", "step", "adapter", "total");
        assertThat(stats.get(1)).isEqualTo(new PhaseStat("move", 3, 20, 30, 30, 30));
        assertThat(stats.get(2).max()).isEqualTo(33);
        assertThat(stats.get(4).max()).isEqualTo(133);
    }

    @Test
    void phaseNanosAreACopy() {
        PhaseTimers timers = new PhaseTimers(List.of("ingest"), clock, 600);
        timers.beginTick();
        timers.beginPhase(0);
        now += 7;
        timers.endPhase(0);
        timers.endTick();
        timers.lastPhaseNanos()[0] = 99;
        assertThat(timers.lastPhaseNanos()).containsExactly(7);
    }
}
