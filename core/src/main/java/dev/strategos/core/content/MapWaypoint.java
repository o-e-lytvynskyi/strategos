package dev.strategos.core.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;

/** A named place on a map that tests and orders use: the block a unit stands in, as {@link StartSlot} counts it. */
@CoreApi
public record MapWaypoint(int x, int y, int z) {
    public static final Codec<MapWaypoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.fieldOf("x").forGetter(MapWaypoint::x),
                    Codec.INT.fieldOf("y").forGetter(MapWaypoint::y),
                    Codec.INT.fieldOf("z").forGetter(MapWaypoint::z))
            .apply(i, MapWaypoint::new));
}
