package dev.strategos.core.client.hud;

/**
 * The HUD's colours and spacing (UI-02's theme tokens), ARGB: one place to restyle every layer. Faction colours match
 * the bodies' outline colours.
 */
public record HudTheme(int panel, int border, int text, int dimText, int accent, int padding) {
    public static final HudTheme DEFAULT = new HudTheme(0xB0101418, 0xFF3A4450, 0xFFE8ECF0, 0xFF9AA4AE, 0xFFE0B040, 3);

    /** A faction's colour, opaque: blue, red, green, yellow, purple, orange, cyan; white beyond the seven. */
    public int faction(int faction) {
        return 0xFF000000
                | switch (faction) {
                    case 0 -> 0x3B82F6;
                    case 1 -> 0xEF4444;
                    case 2 -> 0x22C55E;
                    case 3 -> 0xEAB308;
                    case 4 -> 0xA855F7;
                    case 5 -> 0xF97316;
                    case 6 -> 0x06B6D4;
                    default -> 0xFFFFFF;
                };
    }
}
