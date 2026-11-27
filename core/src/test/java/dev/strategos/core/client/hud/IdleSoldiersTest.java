package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.client.select.UnitRole;
import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.UnitSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * UI-17.3 in L1 (the on-screen check is the consent batch's): the idle-soldier key cycles the faction's idle soldiers
 * deterministically in index order and wraps; busy units, workers, other factions' and units on a standing order are
 * skipped; the idle-worker key skips a worker on a standing order too.
 */
class IdleSoldiersTest {
    static final TemplateSet T = TemplateSet.builtin();
    static final int WORKER = IdleWorkersTest.WORKER;
    static final int INFANTRY = IdleWorkersTest.INFANTRY;
    static final int WALKING = 1;

    static UnitSummary.Entry unit(int index, int faction, int template, int state) {
        return IdleWorkersTest.unit(index, faction, template, state);
    }

    private static int next(IdleWorkers key, List<UnitSummary.Entry> units) {
        return Objects.requireNonNull(key.next(units, 0, T)).index();
    }

    /** A unit that holds a patrol or a guard, as the summary flags it. */
    static UnitSummary.Entry standing(int index, int template) {
        return new UnitSummary.Entry(index, 0, template, IdleWorkers.IDLE, index * 4, 8, 0, true);
    }

    @Test
    void theSoldierKeyCyclesInIndexOrderAndWraps() {
        List<UnitSummary.Entry> units = new ArrayList<>(List.of(
                unit(9, 0, INFANTRY, IdleWorkers.IDLE),
                unit(2, 0, INFANTRY, IdleWorkers.IDLE),
                unit(5, 0, INFANTRY, WALKING), // busy
                unit(6, 0, WORKER, IdleWorkers.IDLE), // a worker is no soldier
                unit(7, 1, INFANTRY, IdleWorkers.IDLE), // another faction's
                unit(12, 0, INFANTRY, IdleWorkers.IDLE)));
        IdleWorkers key = new IdleWorkers(UnitRole.SOLDIER);

        List<Integer> visited = new ArrayList<>();
        for (int k = 0; k < 7; k++) {
            visited.add(next(key, units));
        }
        assertThat(visited).containsExactly(2, 9, 12, 2, 9, 12, 2);

        units.set(2, unit(5, 0, INFANTRY, IdleWorkers.IDLE)); // the walker stops
        assertThat(next(key, units)).as("the newcomer after 2").isEqualTo(5);
        assertThat(key.next(List.of(unit(3, 0, INFANTRY, WALKING)), 0, T))
                .as("none idle")
                .isNull();
    }

    @Test
    void aSoldierOnAStandingOrderIsNotIdle() {
        List<UnitSummary.Entry> units = List.of(
                standing(2, INFANTRY), // the guard, standing beside its charge: reports idle
                unit(4, 0, INFANTRY, IdleWorkers.IDLE),
                unit(9, 0, INFANTRY, IdleWorkers.IDLE));
        IdleWorkers key = new IdleWorkers(UnitRole.SOLDIER);

        assertThat(next(key, units)).isEqualTo(4);
        assertThat(next(key, units)).isEqualTo(9);
        assertThat(next(key, units)).as("wraps past the guard").isEqualTo(4);
        assertThat(IdleWorkers.idle(units, 0, T, UnitRole.SOLDIER))
                .extracting(UnitSummary.Entry::index)
                .containsExactly(4, 9);
    }

    @Test
    void aWorkerOnAStandingOrderIsNotIdleEither() {
        List<UnitSummary.Entry> units = List.of(standing(2, WORKER), unit(3, 0, WORKER, IdleWorkers.IDLE));

        assertThat(next(new IdleWorkers(), units)).isEqualTo(3);
        assertThat(IdleWorkers.idle(units, 0, T))
                .extracting(UnitSummary.Entry::index)
                .containsExactly(3);
    }
}
