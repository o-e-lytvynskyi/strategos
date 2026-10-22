package dev.strategos.mod.server.net.payload;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A server-to-client payload reached this client, on its main thread. Registration is common code, which may not name
 * the client's classes (B5), so each such payload's handler posts this event on the game bus for the client to take.
 */
public final class PayloadArrived extends Event {
    private final CustomPacketPayload payload;

    public PayloadArrived(CustomPacketPayload payload) {
        this.payload = payload;
    }

    public CustomPacketPayload payload() {
        return payload;
    }

    /** The handler every server-to-client payload registers with. */
    public static void post(CustomPacketPayload payload, IPayloadContext context) {
        NeoForge.EVENT_BUS.post(new PayloadArrived(payload));
    }
}
