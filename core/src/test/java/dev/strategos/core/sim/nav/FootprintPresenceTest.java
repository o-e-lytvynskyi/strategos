package dev.strategos.core.sim.nav;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.KernelAccess;
import dev.strategos.core.sim.MatchState;
import dev.strategos.core.testkit.SimHarness;
import org.junit.jupiter.api.Test;

class FootprintPresenceTest {
    @Test
    void presenceTracksRestorationMultiplePagesAndTheLastRelease() {
        MatchState s = KernelAccess.state(SimHarness.builder().build().core());
        assertThat(s.hasFootprints()).isFalse();
        s.restoreColumn(1, 1);
        s.restoreColumn(17, 17);
        assertThat(s.hasFootprints()).isTrue();
        s.releaseColumn(1, 1);
        assertThat(s.hasFootprints()).isTrue();
        s.releaseColumn(17, 17);
        assertThat(s.hasFootprints()).isFalse();
        s.releaseColumn(17, 17);
        assertThat(s.hasFootprints()).isFalse();
        s.blockColumn(1, 1);
        s.blockColumn(1, 1);
        s.releaseColumn(1, 1);
        assertThat(s.hasFootprints()).isTrue();
        s.releaseColumn(1, 1);
        assertThat(s.hasFootprints()).isFalse();
    }
}
