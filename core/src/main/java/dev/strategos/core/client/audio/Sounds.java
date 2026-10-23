package dev.strategos.core.client.audio;

import dev.strategos.core.protocol.CommandOutcome;
import dev.strategos.core.protocol.FxKind;

/**
 * Which vanilla sound each order outcome and {@code fx} event plays (AUD-01), and how loud it is played so that it
 * carries from the camera's height.
 */
public final class Sounds {
    /** Where a sound's volume sits in the client's options: the interface's, or the units' (friendly creatures). */
    public enum Category {
        UI,
        UNITS
    }

    /** A vanilla sound event by id, its options category, its volume and pitch. */
    public record Cue(String sound, Category category, float volume, float pitch) {}

    public static final Cue ORDER_ACCEPTED = new Cue("minecraft:entity.villager.yes", Category.UI, 0.6F, 1.0F);
    public static final Cue ORDER_REFUSED = new Cue("minecraft:entity.villager.no", Category.UI, 0.6F, 1.0F);
    public static final Cue UNIT_HIT = new Cue("minecraft:entity.player.hurt", Category.UNITS, 0.8F, 1.0F);
    public static final Cue UNIT_DIED = new Cue("minecraft:entity.player.death", Category.UNITS, 1.0F, 1.0F);

    /** Vanilla's distance over which a sound at volume 1 fades out. */
    public static final double ATTENUATION = 16;
    /** How far a unit's sound carries, as a multiple of the distance from the camera to the ground it looks at. */
    public static final double REACH = 2.5;

    private Sounds() {}

    /** A unit event's cue; a block's event sounds its block's own sound type (AUD-04), so it has none here. */
    public static Cue of(FxKind kind) {
        return switch (kind) {
            case UNIT_HIT -> UNIT_HIT;
            case UNIT_DIED -> UNIT_DIED;
            case BLOCK_CRACK, BLOCK_BREAK, BLOCK_PLACE ->
                throw new IllegalArgumentException(kind + " sounds its block's own sound type");
        };
    }

    /** An order's acknowledgement: accepted, even in part, or refused. */
    public static Cue of(CommandOutcome outcome) {
        return switch (outcome) {
            case ACCEPTED, PARTIAL -> ORDER_ACCEPTED;
            case REJECTED -> ORDER_REFUSED;
        };
    }

    /**
     * The volume to play {@code cue} at from a camera {@code height} blocks over the ground, looking down at
     * {@code pitch} degrees. Vanilla fades a sound linearly over its volume (at least 1) times 16 blocks and caps its
     * loudness at 1, so a volume above 1 only carries it farther: here, {@link #REACH} times the distance to the
     * ground in view, so a sound there plays at 60 % and one as far again past it still faintly. Close to the ground,
     * as in first person, it is the cue's own.
     */
    public static float volume(Cue cue, double height, double pitch) {
        return volume(cue.volume(), height, pitch);
    }

    /** As {@link #volume(Cue, double, double)}, for a sound of volume {@code base}: a block's (AUD-04). */
    public static float volume(float base, double height, double pitch) {
        double sin = Math.sin(Math.toRadians(Math.max(5, Math.min(90, pitch))));
        double focus = Math.max(0, height) / sin;
        return (float) Math.max(base, REACH * focus / ATTENUATION);
    }
}
