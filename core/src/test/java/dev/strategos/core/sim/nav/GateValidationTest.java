package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A gate is checked before anything is blocked: owner, bounds, duplicates, the cap of 64 (M9 spec §2.9, §6). */
class GateValidationTest {
    private static MatchState state() {
        return KernelAccess.state(
                SimHarness.builder().size(96, 96).factions(2).build().core());
    }

    private static int col(int x, int z) {
        return x << 16 | z;
    }

    @Test
    void aNonPlayerOwnerIsRefusedAndNothingIsBlocked() {
        MatchState s = state();
        for (int owner : new int[] {-1, 2, 7}) { // 2 is Gaia
            assertThatThrownBy(() -> s.play().addGate(owner, new int[] {col(40, 40)}))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(s.footprintAt(40, 40)).isFalse();
        assertThat(s.play().gates().empty()).isTrue();
    }

    @Test
    void aColumnOutsideTheMatchIsRefusedAndNothingIsBlocked() {
        MatchState s = state();
        assertThatThrownBy(() -> s.play().addGate(0, new int[] {col(40, 40), col(96, 3)}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.play().addGate(0, new int[] {col(40, 40), col(3, 96)}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(s.footprintAt(40, 40)).isFalse();
        assertThat(s.play().gates().empty()).isTrue();
    }

    @Test
    void aRepeatedColumnIsRefusedInOneGateAndAcrossGates() {
        MatchState s = state();
        assertThatThrownBy(() -> s.play().addGate(0, new int[] {col(40, 40), col(40, 40)}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(s.footprintAt(40, 40)).isFalse();
        s.play().addGate(0, new int[] {col(40, 40)});
        assertThatThrownBy(() -> s.play().addGate(1, new int[] {col(41, 40), col(40, 40)}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(s.footprintAt(41, 40)).isFalse();
        assertThat(s.play().gates().count()).isEqualTo(1);
    }

    @Test
    void aSixtyFifthGateIsRefused() {
        MatchState s = state();
        for (int k = 0; k < GateOverlay.MAX_GATES; k++) {
            s.play().addGate(0, new int[] {col(k, 10)});
        }
        assertThatThrownBy(() -> s.play().addGate(0, new int[] {col(0, 20)}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(s.play().gates().count()).isEqualTo(64);
        assertThat(s.footprintAt(0, 20)).isFalse();
    }

    @Test
    void aSavedSetThatBreaksTheRulesIsRefusedAndChangesNothing() {
        GateOverlay o = new GateOverlay(2, 96, 96);
        int id = o.add(0, new int[] {col(10, 10)});
        List<List<Long>> bad = List.of(
                List.of(0L, 5L, 1L, (long) col(1, 1)), // owner 5
                List.of(0L, 0L, 1L, (long) col(96, 1)), // outside
                List.of(0L, 0L, 2L, (long) col(1, 1), (long) col(1, 1)), // repeated
                List.of(0L, 0L, 1L, (long) col(1, 1), 1L, 0L, 1L, (long) col(1, 1))); // repeated across gates
        for (List<Long> saved : bad) {
            assertThatThrownBy(() -> o.restore(saved)).isInstanceOf(IllegalArgumentException.class);
            assertThat(o.ids()).containsExactly(id);
        }
    }
}
