package dev.strategos.mod.server.persist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** The core's codecs on NBT: the mod side of "codecs in core, ops in the adapter" (ADR-0018). */
public final class NbtBridge {
    private NbtBridge() {}

    /** An encoding error is a bug, so it throws. */
    public static <T> Tag encode(Codec<T> codec, T value) {
        return codec.encodeStart(NbtOps.INSTANCE, value)
                .getOrThrow(message -> new IllegalStateException("cannot encode to NBT: " + message));
    }

    public static <T> DataResult<T> decode(Codec<T> codec, Tag tag) {
        return codec.parse(NbtOps.INSTANCE, tag);
    }

    public static Dynamic<Tag> dynamic(Tag tag) {
        return new Dynamic<>(NbtOps.INSTANCE, tag);
    }
}
