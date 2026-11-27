package dev.strategos.core.client.select;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.TemplateId;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** UI-17 in L1: select all soldiers and select all workers pick the faction's own units by role, wherever they are. */
class UnitRoleTest {
    static final TemplateSet T = TemplateSet.builtin();

    static int index(String id) {
        return Objects.requireNonNull(T.byId(new TemplateId(id))).netIndex();
    }

    static Seen seen(int handle, int faction, int template, double x) {
        return new Seen(handle, faction, template, x, 0, x, 0);
    }

    @Test
    void soldiersAreTheFightersAndWorkersTheGatherers() {
        int worker = index("strategos:unit/worker");
        int infantry = index("strategos:unit/infantry");
        int archer = index("strategos:unit/archer");
        int horseman = index("strategos:unit/horseman");
        List<Seen> seen = List.of(
                seen(5, 0, worker, 10),
                seen(6, 0, infantry, -4000), // far off screen: still picked
                seen(7, 1, infantry, 10), // another faction's
                seen(8, 0, archer, 10),
                seen(9, 0, horseman, 10),
                seen(10, 0, worker, 10));

        assertThat(UnitRole.SOLDIER.select(seen, 0, T)).containsExactly(6, 8, 9);
        assertThat(UnitRole.WORKER.select(seen, 0, T)).containsExactly(5, 10);
        assertThat(UnitRole.SOLDIER.select(seen, 1, T)).containsExactly(7);
        assertThat(UnitRole.SOLDIER.select(List.of(), 0, T)).isEmpty();
        assertThat(UnitRole.WORKER.has(T, -1)).as("a template the set lacks").isFalse();
    }
}
