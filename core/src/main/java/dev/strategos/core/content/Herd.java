package dev.strategos.core.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.shared.CoreApi;
import dev.strategos.core.shared.TemplateId;

/**
 * A wild herd a map places (ECO-10): {@code count} animals of {@code template}, Gaia's, scattered within {@code spread}
 * blocks of the match-local place ({@code x}, {@code z}) when the match starts, where the map's own coordinates are
 * written, so each slot's herds are written out for the slot.
 */
@CoreApi
public record Herd(TemplateId template, double x, double z, int count, double spread) {
    public static final Codec<Herd> CODEC = RecordCodecBuilder.create(i -> i.group(
                    TemplateId.CODEC.fieldOf("template").forGetter(Herd::template),
                    Codec.DOUBLE.fieldOf("x").forGetter(Herd::x),
                    Codec.DOUBLE.fieldOf("z").forGetter(Herd::z),
                    Codec.intRange(1, 64).fieldOf("count").forGetter(Herd::count),
                    Codec.doubleRange(0, 32).optionalFieldOf("spread", 2.0).forGetter(Herd::spread))
            .apply(i, Herd::new));
}
