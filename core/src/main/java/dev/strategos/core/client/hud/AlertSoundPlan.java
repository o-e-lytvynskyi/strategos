package dev.strategos.core.client.hud;

import dev.strategos.core.shared.CoreApi;
import java.util.EnumMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Which sound each feed entry makes and how often (UI-21, spec §2.12): under attack the bell, a wave the raid horn, a
 * phase the challenge toast, research the level-up chime, training a villager's cheer, a building the anvil; the
 * economy's notices are silent. Alerts play under the hostile category, the rest under neutral, so vanilla's sliders
 * set them. Each sound plays at most once per {@value #EVERY_TICKS} client ticks (three seconds), by its own limiter.
 * Pure: the client resolves the registry ids to engine sounds.
 */
@CoreApi
public final class AlertSoundPlan {
    public static final int EVERY_TICKS = 60;

    /** The engine's sound category a cue plays under. */
    @CoreApi
    public enum Category {
        HOSTILE,
        NEUTRAL
    }

    /** A sound to play: its registry id, category, volume and pitch. */
    @CoreApi
    public record Cue(String sound, Category category, float volume, float pitch) {}

    private final Map<NotificationFeed.Kind, Long> last = new EnumMap<>(NotificationFeed.Kind.class);

    /** The cue of {@code kind}, or null when it is silent. */
    public static @Nullable Cue cue(NotificationFeed.Kind kind) {
        return switch (kind) {
            case UNDER_ATTACK -> new Cue("minecraft:block.bell.use", Category.HOSTILE, 1.0F, 0.8F);
            case WAVE -> new Cue("minecraft:event.raid.horn", Category.HOSTILE, 1.0F, 1.0F);
            case PHASE -> new Cue("minecraft:ui.toast.challenge_complete", Category.NEUTRAL, 1.0F, 1.0F);
            case RESEARCH -> new Cue("minecraft:entity.player.levelup", Category.NEUTRAL, 0.6F, 1.0F);
            case TRAINED -> new Cue("minecraft:entity.villager.celebrate", Category.NEUTRAL, 0.6F, 1.0F);
            case BUILT -> new Cue("minecraft:block.anvil.use", Category.NEUTRAL, 0.4F, 1.2F);
            case ECONOMY -> null;
        };
    }

    /** The cue to play for {@code kind} at client tick {@code now}, or null when silent or played within three seconds. */
    public @Nullable Cue next(NotificationFeed.Kind kind, long now) {
        Cue cue = cue(kind);
        if (cue == null) {
            return null;
        }
        Long before = last.get(kind);
        if (before != null && now - before < EVERY_TICKS) {
            return null;
        }
        last.put(kind, now);
        return cue;
    }
}
