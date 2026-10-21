package dev.strategos.core.client.archfixture;

import dev.strategos.core.sim.MatchLifecycle;

/** B3's violating fixture: client prediction reaching into the simulation. */
public final class SimReferenceFixture {
    private SimReferenceFixture() {}

    static MatchLifecycle lifecycle() {
        return MatchLifecycle.RUNNING;
    }
}
