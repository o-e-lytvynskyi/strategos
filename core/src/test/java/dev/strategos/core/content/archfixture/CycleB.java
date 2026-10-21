package dev.strategos.core.content.archfixture;

import dev.strategos.core.diag.archfixture.CycleA;

/** The other half of B3+'s cycle fixture. */
public final class CycleB {
    private CycleB() {}

    static CycleA other() {
        return CycleA.make();
    }

    public static CycleB make() {
        return new CycleB();
    }
}
