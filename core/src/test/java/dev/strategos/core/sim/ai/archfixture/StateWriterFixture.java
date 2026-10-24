package dev.strategos.core.sim.ai.archfixture;

import dev.strategos.core.sim.MatchState;

/** A computer player's module that reaches for the match's state: AI_NEVER_WRITES_STATE must flag it. */
public final class StateWriterFixture {
    private StateWriterFixture() {}

    public static long tick(MatchState state) {
        return state.tick();
    }
}
