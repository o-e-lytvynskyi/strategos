package dev.strategos.mod.archfixture;

import net.minecraft.client.Minecraft;

/** B5's violating fixture: common code that touches a client class. */
public final class ClientReferenceFixture {
    private ClientReferenceFixture() {}

    static Object client() {
        return Minecraft.getInstance();
    }
}
