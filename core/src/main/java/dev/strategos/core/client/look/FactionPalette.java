package dev.strategos.core.client.look;

import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.protocol.FactionPresentation;
import java.util.Objects;

/** Selected vanilla dye palette, with exact released appearance for legacy matches. */
public record FactionPalette(FactionPresentation presentation) {
    // Minecraft 1.21.1 DyeColor ids and RGB values, verified against the cached engine sources.
    private enum Dye {
        WHITE(0xF9FFFE, "white"),
        ORANGE(0xF9801D, "orange"),
        MAGENTA(0xC74EBD, "magenta"),
        LIGHT_BLUE(0x3AB3DA, "light_blue"),
        YELLOW(0xFED83D, "yellow"),
        LIME(0x80C71F, "lime"),
        PINK(0xF38BAA, "pink"),
        GRAY(0x474F52, "gray"),
        LIGHT_GRAY(0x9D9D97, "light_gray"),
        CYAN(0x169C9C, "cyan"),
        PURPLE(0x8932B8, "purple"),
        BLUE(0x3C44AA, "blue"),
        BROWN(0x835432, "brown"),
        GREEN(0x5E7C16, "green"),
        RED(0xB02E26, "red"),
        BLACK(0x1D1D21, "black");
        private final int rgb;
        private final String name;

        Dye(int rgb, String name) {
            this.rgb = rgb;
            this.name = name;
        }

        static Dye of(int id) {
            return switch (id) {
                case 0 -> WHITE;
                case 1 -> ORANGE;
                case 2 -> MAGENTA;
                case 3 -> LIGHT_BLUE;
                case 4 -> YELLOW;
                case 5 -> LIME;
                case 6 -> PINK;
                case 7 -> GRAY;
                case 8 -> LIGHT_GRAY;
                case 9 -> CYAN;
                case 10 -> PURPLE;
                case 11 -> BLUE;
                case 12 -> BROWN;
                case 13 -> GREEN;
                case 14 -> RED;
                case 15 -> BLACK;
                default -> throw new IllegalArgumentException("colour outside palette");
            };
        }
    }

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
                ? (0xFF000000 | Dye.of(presentation.style(faction).colour()).rgb)
                : HudTheme.DEFAULT.faction(faction);
    }

    public String dyeName(int faction) {
        return hasExplicitColour(faction)
                ? Dye.of(presentation.style(faction).colour()).name
                : UnitLook.dyeName(faction);
    }
}
