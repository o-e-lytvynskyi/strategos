package dev.strategos.mod.server.net;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.sim.MatchLifecycle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MatchControlPolicyTest {
    @ParameterizedTest
    @CsvSource({
        "true, false, true, true",
        "true, true, true, false",
        "true, false, false, false",
        "true, true, false, false",
        "false, false, true, false",
        "false, true, true, false",
        "false, false, false, false",
        "false, true, false, false"
    })
    void onlyTheOwnerOfAnUnpublishedIntegratedWorldCanControlItsLiveMatch(
            boolean singleplayer, boolean published, boolean owner, boolean allowed) {
        assertThat(MatchControlPolicy.allows(singleplayer, published, owner, MatchLifecycle.RUNNING))
                .isEqualTo(allowed);
        assertThat(MatchControlPolicy.allows(singleplayer, published, owner, MatchLifecycle.PAUSED))
                .isEqualTo(allowed);
    }

    @Test
    void evenThePrivateOwnerCannotControlAMissingOrInactiveMatch() {
        assertThat(MatchControlPolicy.allows(true, false, true, null)).isFalse();
        for (var lifecycle : MatchLifecycle.values()) {
            if (lifecycle != MatchLifecycle.RUNNING && lifecycle != MatchLifecycle.PAUSED) {
                assertThat(MatchControlPolicy.allows(true, false, true, lifecycle))
                        .as(lifecycle.name())
                        .isFalse();
            }
        }
    }
}
