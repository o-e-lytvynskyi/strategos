package dev.strategos.core.sim.formation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.FormationShape;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.FactionSlot;
import dev.strategos.core.shared.Handle;
import dev.strategos.core.sim.replicate.ReplicationService;
import dev.strategos.core.sim.replicate.Visibility;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** ORD-06: a unit's formation reaches its own faction's summary, and no other viewer's. */
class FormationReplicationTest {
    @Test
    void aMemberCarriesItsFormationInItsOwnFactionsFrameOnly() {
        FormationFixtures f = FormationFixtures.flat(true);
        int[] marching = f.block(FormationFixtures.HORSEMAN, 6, 3, 1.5, 20, 20);
        int[] idle = f.block(FormationFixtures.ARCHER, 2, 2, 1.5, 40, 20);
        FormationController formation = f.march(marching, 100, 20, FormationShape.BOX);

        UnitSummary own = ReplicationService.summary(f.h.queries(), 1, new FactionSlot(0), Visibility.ALL);
        for (int handle : marching) {
            assertThat(entry(own, handle).formation()).as("member %d", handle).isEqualTo(formation.id());
        }
        for (int handle : idle) {
            assertThat(entry(own, handle).formation()).as("a unit in none").isZero();
        }

        assertThat(f.h.queries().formationSlotError(marching[0])).isFinite().isLessThan(10);
        assertThat(f.h.queries().formationSlotError(idle[0])).as("in none").isNaN();

        UnitSummary rival = ReplicationService.summary(f.h.queries(), 1, new FactionSlot(1), Visibility.ALL);
        assertThat(rival.units()).hasSize(8).allMatch(e -> e.formation() == 0);
        UnitSummary observer = ReplicationService.summary(f.h.queries(), 1, null, Visibility.ALL);
        assertThat(observer.units()).allMatch(e -> e.formation() == 0);
    }

    private static UnitSummary.Entry entry(UnitSummary summary, int handle) {
        return summary.units().stream()
                .filter(e -> e.index() == Handle.index(handle))
                .findFirst()
                .orElseThrow(() -> new AssertionError(Arrays.toString(new int[] {handle})));
    }
}
