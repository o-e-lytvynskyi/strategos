package dev.strategos.core.protocol;

import dev.strategos.core.shared.Hasher;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Immutable public faction identity; no player names, authority or private economy. */
public final class FactionPresentation {
    public record Style(String civilisation, int colour) {
        public Style {
            if (civilisation.length() > 128 || !civilisation.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("civilisation must be a bounded resource id");
            if (colour < -1 || colour > 15)
                throw new IllegalArgumentException("colour outside legacy or palette bounds");
        }
    }

    private final List<Style> styles;
    private final boolean legacy;
    private final long hash;

    public FactionPresentation(List<Style> styles) {
        if (styles.isEmpty() || styles.size() > 16)
            throw new IllegalArgumentException("presentation needs 1..16 factions");
        this.styles = List.copyOf(styles);
        boolean defaults = true;
        Hasher hasher = new Hasher(0x464143544C4F4F4BL);
        hasher.putInt(styles.size());
        for (Style style : this.styles) {
            defaults &= style.colour() == -1 && style.civilisation().equals("strategos:placeholder");
            hasher.putString(style.civilisation()).putInt(style.colour());
        }
        legacy = defaults;
        hash = hasher.finish();
    }

    public static FactionPresentation legacy(int factions) {
        if (factions < 1 || factions > 16) throw new IllegalArgumentException("presentation needs 1..16 factions");
        return new FactionPresentation(java.util.Collections.nCopies(factions, new Style("strategos:placeholder", -1)));
    }

    public List<Style> styles() {
        return styles;
    }

    public Style style(int faction) {
        return styles.get(faction);
    }

    public boolean isLegacy() {
        return legacy;
    }

    /** Cached canonical configuration hash; default presentation contributes nothing to released match hashes. */
    public long mixHash(long state) {
        return Hasher.mix(state, hash);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof FactionPresentation p && styles.equals(p.styles));
    }

    @Override
    public int hashCode() {
        return styles.hashCode();
    }

    public static final WireCodec<FactionPresentation> WIRE = new WireCodec<>() {
        @Override
        public void write(WireWriter out, FactionPresentation value) {
            out.writeVarInt(value.styles.size());
            for (Style style : value.styles)
                out.writeString(style.civilisation(), 128).writeZigZag(style.colour());
        }

        @Override
        public FactionPresentation read(WireReader in) {
            int count = in.readVarInt(16);
            if (count == 0) throw new WireException("empty faction presentation");
            var styles = new ArrayList<Style>(count);
            try {
                for (int i = 0; i < count; i++) styles.add(new Style(in.readString(128), in.readZigZagInt()));
                return new FactionPresentation(styles);
            } catch (IllegalArgumentException invalid) {
                throw new WireException("invalid faction presentation");
            }
        }

        @Override
        public int maxSize() {
            return 1 + 16 * (2 + 128 + 1);
        }
    };
}
