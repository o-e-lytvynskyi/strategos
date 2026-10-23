package dev.strategos.core.client.hud;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.protocol.UnitSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** UI-10.1: the idle-worker key visits every idle worker of its faction once, in index order, then starts again. */
class IdleWorkersTest {
    static final TemplateSet T = TemplateSet.builtin();
    static final int WORKER = Objects.requireNonNull(T.byId(TemplateSet.WORKER)).netIndex();
    static final int INFANTRY =
            Objects.requireNonNull(T.byId(TemplateSet.INFANTRY)).netIndex();

    static UnitSummary.Entry unit(int index, int faction, int template, int state) {
        return new UnitSummary.Entry(index, faction, template, state, index * 4, 8);
    }

    @Test
    void theKeyCyclesInStableOrder() {
        List<UnitSummary.Entry> units = new ArrayList<>(List.of(
                unit(9, 0, WORKER, IdleWorkers.IDLE),
                unit(2, 0, WORKER, IdleWorkers.IDLE),
                unit(5, 0, WORKER, 3), // gathering
                unit(7, 1, WORKER, IdleWorkers.IDLE), // another faction's
                unit(4, 0, INFANTRY, IdleWorkers.IDLE), // a spearman gathers nothing by default
                unit(12, 0, WORKER, IdleWorkers.IDLE)));
        IdleWorkers key = new IdleWorkers();
        List<Integer> visited = new ArrayList<>();
        for (int k = 0; k < 6; k++) {
            visited.add(Objects.requireNonNull(key.next(units, 0, T)).index());
        }
        assertThat(visited).containsExactly(2, 9, 12, 2, 9, 12);

        units.set(2, unit(5, 0, WORKER, IdleWorkers.IDLE)); // the gatherer goes idle after 12 was taken
        assertThat(Objects.requireNonNull(key.next(units, 0, T)).index())
                .as("round to the first")
                .isEqualTo(2);
        assertThat(Objects.requireNonNull(key.next(units, 0, T)).index())
                .as("then the newcomer")
                .isEqualTo(5);
        units.removeIf(u -> u.index() == 9);
        assertThat(Objects.requireNonNull(key.next(units, 0, T)).index())
                .as("a busy one skipped")
                .isEqualTo(12);
        assertThat(key.next(List.of(unit(3, 0, WORKER, 1)), 0, T))
                .as("none idle")
                .isNull();
        assertThat(IdleWorkers.idle(units, 0, T))
                .extracting(UnitSummary.Entry::index)
                .containsExactly(2, 5, 12);
    }
}
