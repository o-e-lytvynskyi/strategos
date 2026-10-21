package dev.strategos.core.sim.archfixture;

import dev.strategos.core.client.archfixture.ClientStub;

/** B3's violating fixture: the simulation reaching into client prediction. */
public final class ClientReferenceFixture {
    private ClientReferenceFixture() {}

    static ClientStub predicted() {
        return new ClientStub();
    }
}
