package dev.strategos.core.shared;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** A point on a match's ground plane, in blocks: match-local X and Z. */
public record Vec2d(double x, double z) {
    public static final Codec<Vec2d> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.DOUBLE.fieldOf("x").forGetter(Vec2d::x),
                    Codec.DOUBLE.fieldOf("z").forGetter(Vec2d::z))
            .apply(i, Vec2d::new));

    public double distanceTo(Vec2d o) {
        return DetMath.length(o.x - x, o.z - z);
    }
}
