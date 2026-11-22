package dev.strategos.mod.server.net.payload;

import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireReader;
import dev.strategos.core.protocol.WireWriter;
import dev.strategos.mod.platform.Ids;
import dev.strategos.mod.server.net.ByteBufWire;
import dev.strategos.mod.server.net.MatchScopedPayload;
import dev.strategos.mod.server.net.PayloadSpec;
import dev.strategos.mod.server.net.SessionEnvelope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/** A map-local block selected by the player. The server supplies match, sender and audience. */
public record PingPayload(long epoch, @Nullable Point point) implements MatchScopedPayload {
    public record Point(int x, int z) {}

    private static final WireCodec<SessionEnvelope<Point>> WIRE = SessionEnvelope.codec(new WireCodec<>() {
        @Override
        public void write(WireWriter out, Point point) {
            out.writeInt(point.x()).writeInt(point.z());
        }

        @Override
        public Point read(WireReader in) {
            return new Point(in.readInt(), in.readInt());
        }

        @Override
        public int maxSize() {
            return 2 * Integer.BYTES;
        }
    });

    public static final Type<PingPayload> TYPE = new Type<>(Ids.id("ping"));
    public static final PayloadSpec SPEC = PayloadSpec.toServer(TYPE.id(), WIRE.maxSize());
    public static final StreamCodec<RegistryFriendlyByteBuf, PingPayload> STREAM_CODEC = ByteBufWire.serverCodec(
            WIRE,
            frame -> new PingPayload(frame.epoch(), frame.body()),
            p -> p.point == null ? null : new SessionEnvelope<>(p.epoch, p.point),
            () -> new PingPayload(0, null));

    public PingPayload(long epoch, int x, int z) {
        this(epoch, new Point(x, z));
    }

    @Override
    public boolean decoded() {
        return point != null;
    }

    @Override
    public PingPayload withEpoch(long epoch) {
        return new PingPayload(epoch, point);
    }

    @Override
    public Type<PingPayload> type() {
        return TYPE;
    }
}
