package dev.strategos.mod.server.net;

import dev.strategos.core.protocol.WireCodec;
import dev.strategos.core.protocol.WireException;
import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * A payload's {@link StreamCodec} over a core {@link WireCodec} (ADR-0016): a payload's bytes are its record's, so
 * layouts and sizes are tested in L1; decoding refuses more than the record's bound before it copies a byte.
 */
public final class ByteBufWire {
    private ByteBufWire() {}

    public static <T, P> StreamCodec<RegistryFriendlyByteBuf, P> codec(
            WireCodec<T> wire, Function<T, P> wrap, Function<P, T> unwrap) {
        return StreamCodec.of(
                (buf, payload) -> buf.writeBytes(WireCodec.encode(wire, unwrap.apply(payload))),
                buf -> wrap.apply(read(wire, buf)));
    }

    /** Malformed client input is a consumed refusal, so the server's common violation policy decides its effect. */
    public static <T, P> StreamCodec<RegistryFriendlyByteBuf, P> serverCodec(
            WireCodec<T> wire, Function<T, P> wrap, Function<P, @Nullable T> unwrap, Supplier<P> refused) {
        return StreamCodec.of(
                (buf, payload) -> {
                    var value = unwrap.apply(payload);
                    if (value == null) throw new IllegalStateException("a refused inbound payload is never sent");
                    buf.writeBytes(WireCodec.encode(wire, value));
                },
                buf -> {
                    try {
                        return wrap.apply(read(wire, buf));
                    } catch (WireException | IllegalArgumentException invalid) {
                        buf.skipBytes(buf.readableBytes());
                        return refused.get();
                    }
                });
    }

    /** Exactly one record from the rest of {@code buf}, at most the codec's bound. */
    public static <T> T read(WireCodec<T> wire, ByteBuf buf) {
        int n = buf.readableBytes();
        if (n > wire.maxSize()) {
            throw new WireException(n + " bytes above the bound " + wire.maxSize());
        }
        byte[] bytes = new byte[n];
        buf.readBytes(bytes);
        return WireCodec.decode(wire, bytes);
    }
}
