package dev.strategos.core.sim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.testkit.FakeAllies;
import org.junit.jupiter.api.Test;

/** M9 spec §2.1: the relation seam MATCH-06 will fill. */
class AlliesTest {
    @Test
    void untilTeamsExistAFactionIsAlliedOnlyWithItselfAndGaiaWithNobody() {
        Allies a = Allies.sameFaction(2);
        assertThat(a.allied(0, 0)).isTrue();
        assertThat(a.allied(0, 1)).isFalse();
        assertThat(a.allied(2, 2)).isFalse(); // Gaia
        assertThat(a.enemies(0, 1, 2)).isTrue();
        assertThat(a.enemies(0, 2, 2)).isFalse(); // Gaia is no player's enemy here
    }

    @Test
    void theFakeAlliesTeamUpForTests() {
        Allies a = FakeAllies.twoAgainstRest(3);
        assertThat(a.allied(0, 1)).isTrue();
        assertThat(a.allied(1, 2)).isFalse();
        assertThat(a.allied(0, 3)).isFalse();
    }
}
