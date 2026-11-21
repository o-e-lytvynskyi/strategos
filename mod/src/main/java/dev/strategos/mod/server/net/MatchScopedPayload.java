package dev.strategos.mod.server.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A decoded intent for one authenticated live match view, never for a lobby or a template negotiation. */
public interface MatchScopedPayload extends CustomPacketPayload {
    long epoch();

    boolean decoded();

    MatchScopedPayload withEpoch(long epoch);
}
