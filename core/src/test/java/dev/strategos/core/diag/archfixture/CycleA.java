package dev.strategos.core.diag.archfixture;

import dev.strategos.core.content.archfixture.CycleB;

/** One half of B3+'s cycle fixture: slice diag uses slice content, which uses diag. */
public final class CycleA {
    private CycleA() {}

    static CycleB other() {
        return CycleB.make();
    }

    public static CycleA make() {
        return new CycleA();
    }
}
