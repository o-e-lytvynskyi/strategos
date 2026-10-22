package dev.strategos.core.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;

/**
 * Where a faction starts on a map (04 §3.3): the block a unit stands in, match-local {@code x}, {@code z} and {@code y}
 * from the band's bottom, and the facing in degrees as Minecraft counts yaw (0 south, 90 west, 180 north, 270 east).
 */
@CoreApi
public record StartSlot(int x, int y, int z, float yaw) {
    public static final Codec<StartSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("x").forGetter(StartSlot::x),
                    Codec.INT.fieldOf("y").forGetter(StartSlot::y),
                    Codec.INT.fieldOf("z").forGetter(StartSlot::z),
                    Codec.FLOAT.fieldOf("yaw").forGetter(StartSlot::yaw))
            .apply(i, StartSlot::new));
}
