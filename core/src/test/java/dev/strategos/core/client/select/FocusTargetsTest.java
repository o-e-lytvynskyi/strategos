package dev.strategos.core.client.select;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** ORD-05's marker: which target to ring for a selection, and the ring's shape. */
class FocusTargetsTest {
    @Test
    void aSelectionOfOneFocusShowsItsTarget() {
        FocusTargets focus = new FocusTargets();
        focus.focus(new int[] {1, 2, 3}, 77, false);
        assertThat(focus.shared(List.of(1, 3))).isEqualTo(77);
        assertThat(focus.shared(List.of(2))).isEqualTo(77);
    }

    @Test
    void unitsThatDifferOrHoldNoTargetShowNone() {
        FocusTargets focus = new FocusTargets();
        focus.focus(new int[] {1, 2}, 77, false);
        focus.focus(new int[] {3}, 88, false);
        assertThat(focus.shared(List.of(1, 3))).as("two targets").isNull();
        assertThat(focus.shared(List.of(1, 4))).as("one without").isNull();
        assertThat(focus.shared(List.of())).as("nobody").isNull();
    }

    @Test
    void aSpreadOrAnyOtherOrderReleasesTheFocus() {
        FocusTargets focus = new FocusTargets();
        focus.focus(new int[] {1, 2}, 77, false);
        focus.release(new int[] {2});
        assertThat(focus.shared(List.of(1))).isEqualTo(77);
        assertThat(focus.shared(List.of(1, 2))).isNull();
    }

    @Test
    void aQueuedAttackOnlyFocusesUnitsWithNone() {
        FocusTargets focus = new FocusTargets();
        focus.focus(new int[] {1}, 77, false);
        focus.focus(new int[] {1, 2}, 88, true);
        assertThat(focus.shared(List.of(1))).isEqualTo(77);
        assertThat(focus.shared(List.of(2))).isEqualTo(88);
    }

    @Test
    void theRingIsAClosedBandRoundTheCentre() {
        List<FocusRing.Quad> ring = FocusRing.of(10, 20, 0.6);
        assertThat(ring).hasSize(FocusRing.SEGMENTS);
        for (int k = 0; k < ring.size(); k++) {
            FocusRing.Quad q = ring.get(k);
            FocusRing.Quad next = ring.get((k + 1) % ring.size());
            assertThat(Math.hypot(q.x0() - 10, q.z0() - 20)).isCloseTo(0.6, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(Math.hypot(q.x1() - 10, q.z1() - 20))
                    .isCloseTo(0.6 + NodeRing.WIDTH, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(q.x3()).as("joined to the next").isCloseTo(next.x0(), org.assertj.core.data.Offset.offset(1e-9));
            assertThat(q.z2()).isCloseTo(next.z1(), org.assertj.core.data.Offset.offset(1e-9));
        }
    }
}
