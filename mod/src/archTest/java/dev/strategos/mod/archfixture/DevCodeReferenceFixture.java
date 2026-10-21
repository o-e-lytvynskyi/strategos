package dev.strategos.mod.archfixture;

import dev.strategos.gametest.DevCodeStub;

/** B11's violating fixture: shipped code that depends on dev-only code. */
public final class DevCodeReferenceFixture {
    private DevCodeReferenceFixture() {}

    static Object devCode() {
        return new DevCodeStub();
    }
}
