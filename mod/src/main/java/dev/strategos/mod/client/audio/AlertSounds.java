package dev.strategos.mod.client.audio;

import dev.strategos.core.client.hud.NotificationFeed;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * The feed's sounds (UI-21, spec §2.12): under attack the bell, a wave the raid horn, a phase the challenge toast,
 * research the level-up chime, training a villager's cheer, a building the anvil. Alerts play under {@code HOSTILE},
 * the rest under {@code NEUTRAL}, so vanilla's sliders set them; each sound at most once per {@value #EVERY_TICKS}
 * client ticks, by its own limiter ({@code SoundBudget}'s per-tick cap stays the units'). Played at the listener
 * through {@link FxPlayer}.
 */
public final class AlertSounds {
    static final int EVERY_TICKS = 60;

    private record Cue(SoundEvent sound, SoundSource source, float volume, float pitch) {}

    private final FxPlayer player;
    private final Map<NotificationFeed.Kind, Long> last = new EnumMap<>(NotificationFeed.Kind.class);

    public AlertSounds(FxPlayer player) {
        this.player = player;
    }

    /** Plays {@code kind}'s sound at client tick {@code now} unless it played within three seconds; whether it did. */
    public boolean play(NotificationFeed.Kind kind, long now) {
        Cue cue = cue(kind);
        if (cue == null) {
            return false;
        }
        Long before = last.get(kind);
        if (before != null && now - before < EVERY_TICKS) {
            return false;
        }
        last.put(kind, now);
        player.local(cue.sound(), cue.source(), cue.volume(), cue.pitch());
        return true;
    }

    private static @Nullable Cue cue(NotificationFeed.Kind kind) {
        return switch (kind) {
            case UNDER_ATTACK -> new Cue(SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.0F, 0.8F);
            case WAVE -> new Cue(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
            case PHASE -> new Cue(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 1.0F, 1.0F);
            case RESEARCH -> new Cue(SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6F, 1.0F);
            case TRAINED -> new Cue(SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.6F, 1.0F);
            case BUILT -> new Cue(SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.4F, 1.2F);
            case ECONOMY -> null;
        };
    }
}
