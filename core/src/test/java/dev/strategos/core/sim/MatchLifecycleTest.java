package dev.strategos.core.sim;

import static dev.strategos.core.sim.MatchLifecycle.CREATED;
import static dev.strategos.core.sim.MatchLifecycle.DISPOSED;
import static dev.strategos.core.sim.MatchLifecycle.ENDED;
import static dev.strategos.core.sim.MatchLifecycle.FAULTED;
import static dev.strategos.core.sim.MatchLifecycle.PAUSED;
import static dev.strategos.core.sim.MatchLifecycle.PREPARING;
import static dev.strategos.core.sim.MatchLifecycle.READY;
import static dev.strategos.core.sim.MatchLifecycle.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class MatchLifecycleTest {
    /** The 15 transitions of M0 spec 5.5, written out so the switch is checked against an independent table. */
    private static final List<List<MatchLifecycle>> ALLOWED = List.of(
            List.of(CREATED, PREPARING),
            List.of(CREATED, FAULTED),
            List.of(CREATED, DISPOSED),
            List.of(PREPARING, READY),
            List.of(PREPARING, FAULTED),
            List.of(READY, RUNNING),
            List.of(READY, FAULTED),
            List.of(RUNNING, PAUSED),
            List.of(RUNNING, ENDED),
            List.of(RUNNING, FAULTED),
            List.of(PAUSED, RUNNING),
            List.of(PAUSED, ENDED),
            List.of(PAUSED, FAULTED),
            List.of(ENDED, DISPOSED),
            List.of(FAULTED, DISPOSED));

    @Test
    void allowsExactlyTheFifteenTransitionsOfTheTable() {
        int allowed = 0;
        for (MatchLifecycle from : MatchLifecycle.values()) {
            for (MatchLifecycle to : MatchLifecycle.values()) {
                boolean expected = ALLOWED.contains(List.of(from, to));
                assertThat(from.canTransitionTo(to)).as("%s -> %s", from, to).isEqualTo(expected);
                if (expected) {
                    assertThat(from.transition(to)).isEqualTo(to);
                    allowed++;
                } else {
                    assertThatThrownBy(() -> from.transition(to))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining(from.name())
                            .hasMessageContaining(to.name());
                }
            }
        }
        assertThat(allowed).isEqualTo(15);
    }
}
