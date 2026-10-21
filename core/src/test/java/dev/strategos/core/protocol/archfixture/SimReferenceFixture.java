package dev.strategos.core.protocol.archfixture;

import dev.strategos.core.sim.MatchLifecycle;

/** B3+'s violating fixture: a lower layer depending on the simulation. */
public final class SimReferenceFixture {
    private SimReferenceFixture() {}

    static MatchLifecycle lifecycle() {
        return MatchLifecycle.RUNNING;
    }
}
