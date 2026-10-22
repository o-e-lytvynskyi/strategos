package dev.strategos.mod.server.net;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * What a payload type may do (ADR-0016, spec §2.7): its direction, the audiences a server-to-client one may go to (none
 * for a client-to-server one), and its cap in bytes, which its codec's bound keeps.
 */
public record PayloadSpec(ResourceLocation id, boolean toClient, Set<Audience.Kind> audiences, int cap) {
    public PayloadSpec {
        audiences = audiences.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(audiences));
        if (!toClient && !audiences.isEmpty()) {
            throw new IllegalArgumentException(id + " goes to the server and has no audience");
        }
    }

    public static PayloadSpec toServer(ResourceLocation id, int cap) {
        return new PayloadSpec(id, false, Set.of(), cap);
    }

    public static PayloadSpec toClient(ResourceLocation id, int cap, Audience.Kind... audiences) {
        return new PayloadSpec(id, true, Set.of(audiences), cap);
    }
}
