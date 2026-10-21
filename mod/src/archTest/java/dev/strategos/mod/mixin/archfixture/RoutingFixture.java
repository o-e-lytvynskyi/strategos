package dev.strategos.mod.mixin.archfixture;

import dev.strategos.mod.archfixture.ArchStubService;

/** B10's violating fixture: a mixin calling a mod service instead of a router. */
public final class RoutingFixture {
    private RoutingFixture() {}

    static Object service() {
        return new ArchStubService();
    }
}
