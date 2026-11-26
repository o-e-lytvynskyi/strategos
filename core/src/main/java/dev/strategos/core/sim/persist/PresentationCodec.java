package dev.strategos.core.sim.persist;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.strategos.core.protocol.FactionPresentation;

/** Bounded public presentation, separate from authority and from adapter player names. */
final class PresentationCodec {
    private static final Codec<String> CIVILISATION =
            Codec.STRING.validate(id -> id.length() <= 128 && id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    ? DataResult.success(id)
                    : DataResult.error(() -> "invalid civilisation id"));
    private static final Codec<FactionPresentation.Style> STYLE = RecordCodecBuilder.create(i -> i.group(
                    CIVILISATION.fieldOf("civilisation").forGetter(FactionPresentation.Style::civilisation),
                    Codec.intRange(-1, 15).fieldOf("colour").forGetter(FactionPresentation.Style::colour))
            .apply(i, FactionPresentation.Style::new));
    static final Codec<FactionPresentation> STATE =
            STYLE.listOf(1, 16).xmap(FactionPresentation::new, FactionPresentation::styles);

    private PresentationCodec() {}
}
