package dev.strategos.mod.client.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import dev.strategos.core.protocol.MatchStateMsg;
import dev.strategos.core.sim.DayClock;
import java.util.List;
import org.junit.jupiter.api.Test;

/** MATCH-16: the fallback tint follows the match's clock and shows only when the server asked for it. */
class NightTintTest {
    private static final long MIDNIGHT = DayClock.CYCLE * 3 / 4 - DayClock.START; // time of day 18000

    private static MatchStateMsg state(MatchStateMsg.Lifecycle lifecycle, boolean tint) {
        return new MatchStateMsg(1, lifecycle, 0, null, List.of(), tint);
    }

    @Test
    void theVeilFollowsTheClock() {
        MatchStateMsg on = state(MatchStateMsg.Lifecycle.RUNNING, true);
        assertThat(NightTint.alpha(on, 0)).as("noon").isZero();
        assertThat(NightTint.alpha(on, MIDNIGHT)).as("midnight").isEqualTo(NightTint.MAX_ALPHA);
        long midDusk = (DayClock.DUSK_FROM + DayClock.NIGHT_FROM) / 2 - DayClock.START;
        assertThat(NightTint.alpha(on, midDusk)).as("half way through dusk").isEqualTo(NightTint.MAX_ALPHA / 2);
    }

    @Test
    void nothingIsDrawnWhenTheServerDidNotAskOrTheMatchIsNotRunning() {
        assertThat(NightTint.alpha(state(MatchStateMsg.Lifecycle.RUNNING, false), MIDNIGHT))
                .isZero();
        assertThat(NightTint.alpha(state(MatchStateMsg.Lifecycle.ENDED, true), MIDNIGHT))
                .isZero();
    }
}
