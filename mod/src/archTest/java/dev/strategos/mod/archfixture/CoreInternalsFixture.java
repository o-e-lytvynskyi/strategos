package dev.strategos.mod.archfixture;

import dev.strategos.core.sim.ArchStubInternal;

/** B2's violating fixture: mod code using a core class outside the API. */
public final class CoreInternalsFixture {
    private CoreInternalsFixture() {}

    static Object internal() {
        return new ArchStubInternal();
    }
}
