package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

/** The footprint overlay counts per column: every blocker of a column needs its own release (M9 spec §2.9). */
class FootprintOverlayTest {
    @Test
    void twoOverlappingFootprintsNeedTwoReleasesAndANeighbourColumnIsUntouched() {
        FootprintOverlay o = new FootprintOverlay(2, 2);
        o.block(5, 5);
        o.block(5, 5);
        o.block(6, 5);

        o.release(5, 5);
        assertThat(o.blocked(5, 5)).as("one footprint left").isTrue();
        PageCoord page = new PageCoord(0, 0);
        assertThat(o.of(page).blocked(page, SurfacePage.column(5, 5))).isTrue();
        o.release(5, 5);
        assertThat(o.blocked(5, 5)).isFalse();
        assertThat(o.blocked(6, 5)).as("the other column stays").isTrue();
        assertThat(o.any()).isTrue();
        o.release(6, 5);
        assertThat(o.any()).isFalse();
        o.release(6, 5);
        assertThat(o.any(0)).as("a release of a free column is ignored").isFalse();
    }

    @Test
    void aGateOverAFootprintStaysBlockedUntilBothAreGone() {
        MatchState s =
                KernelAccess.state(SimHarness.builder().size(96, 96).build().core());
        int[] gate = {40 << 16 | 40, 40 << 16 | 41};
        s.blockColumn(40, 40); // the palisade's footprint
        int id = s.play().addGate(0, gate);

        s.play().removeGate(id);
        assertThat(s.footprintAt(40, 40)).as("the footprint still stands").isTrue();
        assertThat(s.footprintAt(40, 41)).as("a gate column outside it is free").isFalse();

        id = s.play().addGate(0, gate);
        s.releaseColumn(40, 40); // the footprint is destroyed
        assertThat(s.footprintAt(40, 40)).as("the gate still stands").isTrue();
        s.play().removeGate(id);
        assertThat(s.footprintAt(40, 40)).isFalse();
        assertThat(s.hasFootprints()).isFalse();
    }
}
