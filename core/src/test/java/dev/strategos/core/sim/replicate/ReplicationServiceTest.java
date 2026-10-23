package dev.strategos.core.sim.replicate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.shared.Vec2d;
import dev.strategos.core.testkit.SimHarness;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * NET-08.2: a faction's frame holds its own units and only the enemies it sees, never a hidden handle; an observer's
 * holds every unit; positions are the units' to an eighth of a block.
 */
class ReplicationServiceTest {
    private static final FactionSlot A = new FactionSlot(0);
    private static final FactionSlot B = new FactionSlot(1);

    @Test
    void ownUnitsAndOnlyVisibleEnemies() {
        SimHarness h = SimHarness.builder().factions(2).build();
        int[] own = h.spawn(A, new Vec2d(8, 8), 5, 2);
        int[] enemies = h.spawn(B, new Vec2d(24, 24), 5, 2);
        h.runTicks(2);
        int hidden = enemies[2];
        Visibility fog = (viewer, unit) -> unit.handle() != hidden;

        UnitSummary forA = ReplicationService.summary(h.queries(), 4, A, fog);
        assertThat(forA.units())
                .extracting(UnitSummary.Entry::index)
                .containsAll(Arrays.stream(own).map(Handle::index).boxed().toList())
                .doesNotContain(Handle.index(hidden))
                .hasSize(9);

        UnitSummary forB = ReplicationService.summary(h.queries(), 4, B, (viewer, unit) -> false);
        assertThat(forB.units()).as("B's own units, no enemy").hasSize(5).allMatch(e -> e.faction() == 1);

        UnitSummary observer = ReplicationService.summary(h.queries(), 4, null, (viewer, unit) -> false);
        assertThat(observer.units()).hasSize(10);
        assertThat(observer.matchId()).isEqualTo(4);
        assertThat(observer.tick()).isEqualTo(h.tick());
    }

    @Test
    void positionsAndTemplatesAreTheUnits() {
        SimHarness h = SimHarness.builder().factions(2).build();
        int unit = h.spawn(A, new Vec2d(10.3, 20.6), 1, 0)[0];
        h.runTicks(1);
        UnitSummary.Entry e = ReplicationService.summary(h.queries(), 1, A, Visibility.ALL)
                .units()
                .get(0);
        var view = h.queries().unit(unit);
        assertThat(view).isNotNull();
        assertThat(Math.abs(e.x() - view.x())).isLessThanOrEqualTo(1 / 16.0);
        assertThat(Math.abs(e.z() - view.z())).isLessThanOrEqualTo(1 / 16.0);
        assertThat(e.template()).isEqualTo(view.templateIndex());
        assertThat(e.state()).isEqualTo(ReplicationService.stateCode(view.state()));
    }
}
