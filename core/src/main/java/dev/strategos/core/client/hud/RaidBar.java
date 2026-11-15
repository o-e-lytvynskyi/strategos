package dev.strategos.core.client.hud;

import dev.strategos.core.protocol.MatchNews;
import org.jspecify.annotations.Nullable;

/**
 * The raid bar (AI-04, M6 §2.19): what an announced wave looks like as a bar, a red one in ten notches like a vanilla
 * raid's. It fills from the announcement to the tick the wave leaves, then, with the wave on its march, empties over
 * {@value #MARCH} ticks, and is gone. It is drawn from the WAVE match news alone (no boss event), so only a participant
 * of the match whose news it is has one, and not the faction whose wave it is.
 *
 * @param fill how full the bar is, 0 to 1
 * @param marching whether the wave has left and the bar counts down
 * @param seconds whole seconds until the wave leaves, 0 once it has
 */
public record RaidBar(float fill, boolean marching, int seconds) {
    /** The notches of the bar: vanilla's NOTCHED_10. */
    public static final int NOTCHES = 10;

    /** How long the bar counts down after the wave left: one minute. */
    public static final long MARCH = 1_200;

    /** The bar for {@code wave} seen from faction {@code own} at match tick {@code now}, or null for none. */
    public static @Nullable RaidBar of(@Nullable MatchNews wave, int own, long now) {
        if (wave == null || wave.kind() != MatchNews.WAVE || wave.faction() == own) {
            return null;
        }
        long span = Math.max(1, wave.ticks() - wave.tick());
        if (now < wave.ticks()) {
            float fill = Math.max(0f, Math.min(1f, (now - wave.tick()) / (float) span));
            return new RaidBar(fill, false, (int) ((wave.ticks() - now + 19) / 20));
        }
        long gone = now - wave.ticks();
        return gone >= MARCH ? null : new RaidBar(1f - gone / (float) MARCH, true, 0);
    }

    /** How many notches are lit: the whole ones the fill covers. */
    public int lit() {
        return (int) Math.floor(fill * NOTCHES + 1e-4);
    }

    /** The x offset of notch {@code k} (1 to {@value #NOTCHES} minus 1) in a bar {@code width} pixels wide. */
    public static int notchAt(int k, int width) {
        return width * k / NOTCHES;
    }
}
