package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.core.shared.PlayerSlot;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.Audience;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.PayloadSpec;
import dev.strategos.mod.server.net.SessionEnvelope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A server-authorized allied marker, addressed to one authenticated view. */
public record TeamPingPayload(long epoch, Ping ping) implements CustomPacketPayload {
    public record Ping(int matchId, PlayerSlot player, int x, int z) {
        public Ping {
            if (matchId <= 0 || player.isAdmin() || x < 0 || z < 0) {
                throw new IllegalArgumentException("invalid team ping");
            }
        }
    }

    public TeamPingPayload {
        if (epoch <= 0) throw new IllegalArgumentException("missing team ping epoch");
    }

    private static final WireCodec<SessionEnvelope<Ping>> WIRE = SessionEnvelope.codec(new WireCodec<>() {
        @Override
        public void write(WireWriter out, Ping ping) {
            out.writeInt(ping.matchId())
                    .writeByte(ping.player().value())
                    .writeInt(ping.x())
                    .writeInt(ping.z());
        }

        @Override
        public Ping read(WireReader in) {
            try {
                return new Ping(in.readInt(), new PlayerSlot(in.readUnsignedByte()), in.readInt(), in.readInt());
            } catch (IllegalArgumentException invalid) {
                throw new WireException("invalid team ping");
            }
        }

        @Override
        public int maxSize() {
            return 3 * Integer.BYTES + 1;
        }
    });

    public static final Type<TeamPingPayload> TYPE = new Type<>(Ids.id("team_ping"));
    public static final PayloadSpec SPEC = PayloadSpec.toClient(TYPE.id(), WIRE.maxSize(), Audience.Kind.PLAYER);
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamPingPayload> STREAM_CODEC = ByteBufWire.codec(
            WIRE,
            frame -> new TeamPingPayload(frame.epoch(), frame.body()),
            p -> new SessionEnvelope<>(p.epoch, p.ping));

    @Override
    public Type<TeamPingPayload> type() {
        return TYPE;
    }
}
