package dev.strategos.mod.client.fx;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.Fx;
import dev.strategos.core.protocol.FxKind;
import org.junit.jupiter.api.Test;

/** CMB-04: the shots in flight on a client, capped, ended by landing time or their impact, cleared with the match. */
class ShotArcsTest {
    private static Fx.Event launch(double fromX, double toX, int flight) {
        return Fx.Event.shot(FxKind.SHOT_LAUNCH, fromX, 3, 5, toX, 3, 9, flight);
    }

    private static Fx.Event impact(double fromX, double toX) {
        return Fx.Event.shot(FxKind.SHOT_IMPACT, fromX, 3, 5, toX, 3, 9, 4);
    }

    @Test
    void theCapDropsTheOldestFirst() {
        ShotArcs arcs = new ShotArcs();
        for (int k = 0; k < ShotArcs.CAP + 3; k++) {
            arcs.launch(1, launch(k, k + 20, 100), 0, 0, 0, 10);
        }
        assertThat(arcs.size()).isEqualTo(ShotArcs.CAP);
        assertThat(arcs.active(10).get(0).ends().x8()).isEqualTo(3 * 8);
    }

    @Test
    void anArcEndsAtItsLandingTime() {
        ShotArcs arcs = new ShotArcs();
        arcs.launch(1, launch(1, 21, 4), 100, 64, 200, 10);
        assertThat(arcs.active(13)).hasSize(1);
        assertThat(arcs.active(13).get(0).arc().fromX()).isEqualTo(101);
        assertThat(arcs.active(13).get(0).fraction(12)).isEqualTo(0.5);
        assertThat(arcs.active(14)).isEmpty();
        assertThat(arcs.size()).isZero();
    }

    @Test
    void theMatchingImpactEndsOnlyItsArc() {
        ShotArcs arcs = new ShotArcs();
        arcs.launch(1, launch(1, 21, 40), 0, 0, 0, 10);
        arcs.launch(1, launch(2, 22, 40), 0, 0, 0, 10);
        arcs.impact(1, impact(5, 25)); // other ends
        arcs.impact(2, impact(1, 21)); // other match
        assertThat(arcs.size()).isEqualTo(2);
        arcs.impact(1, impact(1, 21));
        assertThat(arcs.active(11)).hasSize(1);
        assertThat(arcs.active(11).get(0).ends().x8()).isEqualTo(2 * 8);
    }

    @Test
    void aNewMatchOrTheEndClearsThem() {
        ShotArcs arcs = new ShotArcs();
        arcs.launch(1, launch(1, 21, 40), 0, 0, 0, 10);
        arcs.joined(1);
        assertThat(arcs.size()).isEqualTo(1);
        arcs.joined(2);
        assertThat(arcs.size()).isZero();
        arcs.launch(2, launch(1, 21, 40), 0, 0, 0, 10);
        arcs.launch(3, launch(1, 21, 40), 0, 0, 0, 10); // a launch of another match starts afresh
        assertThat(arcs.size()).isEqualTo(1);
        arcs.clear();
        assertThat(arcs.size()).isZero();
    }
}
