package dev.strategos.mod.archfixture;

import dev.strategos.mod.client.ArchStubClient;

/** B5+'s violating fixture: common code referencing the mod's client package. */
public final class ModClientReferenceFixture {
    private ModClientReferenceFixture() {}

    static Object client() {
        return new ArchStubClient();
    }
}
