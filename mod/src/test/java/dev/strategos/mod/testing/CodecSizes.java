package dev.strategos.mod.testing;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Encoded sizes of network codecs, held to their caps (QA-03.2). */
public final class CodecSizes {
    private CodecSizes() {}

    /** The bytes {@code value} takes through {@code codec}. */
    public static <T> int encodedSize(StreamCodec<? super FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buf, value);
            return buf.readableBytes();
        } finally {
            buf.release();
        }
    }

    /** Fails, naming the value's type, its size and the cap, when {@code value} encodes to more than {@code max} bytes. */
    public static <T> void assertEncodedSize(StreamCodec<? super FriendlyByteBuf, T> codec, T value, int max) {
        int size = encodedSize(codec, value);
        if (size > max) {
            throw new AssertionError(value.getClass().getSimpleName() + " encoded to " + size + " bytes, cap " + max);
        }
    }
}
