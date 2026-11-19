package dev.strategos.core.client.look;

import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.protocol.FactionPresentation;
import java.util.Objects;

/** Selected vanilla dye palette, with exact released appearance for legacy matches. */
public record FactionPalette(FactionPresentation presentation) {
    // Minecraft 1.21.1 DyeColor ids and texture RGB values, verified against the cached engine sources.
    private static final int[] RGB = {
        0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52, 0x9D9D97, 0x169C9C, 0x8932B8,
        0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21
    };
    private static final String[] NAMES = {
        "white",
        "orange",
        "magenta",
        "light_blue",
        "yellow",
        "lime",
        "pink",
        "gray",
        "light_gray",
        "cyan",
        "purple",
        "blue",
        "brown",
        "green",
        "red",
        "black"
    };

    public FactionPalette {
        Objects.requireNonNull(presentation);
    }

    public boolean hasExplicitColour(int faction) {
        return faction >= 0
                && faction < presentation.styles().size()
                && presentation.style(faction).colour() >= 0;
    }

    public int colour(int faction) {
        return hasExplicitColour(faction)
                ? (0xFF000000 | RGB[presentation.style(faction).colour()])
                : HudTheme.DEFAULT.faction(faction);
    }

    public String dyeName(int faction) {
        return hasExplicitColour(faction) ? NAMES[presentation.style(faction).colour()] : UnitLook.dyeName(faction);
    }
}
