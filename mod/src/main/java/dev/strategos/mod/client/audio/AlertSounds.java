package dev.strategos.mod.client.audio;

import dev.strategos.core.client.hud.AlertSoundPlan;
import dev.strategos.core.client.hud.NotificationFeed;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * The feed's sounds (UI-21, spec §2.12): the adapter of {@link AlertSoundPlan}, which holds the mapping from a feed
 * entry to a sound and its three-second limit. Here the plan's registry ids and categories become engine sounds,
 * played at the listener through {@link FxPlayer}.
 */
public final class AlertSounds {
    private final FxPlayer player;
    private final AlertSoundPlan plan = new AlertSoundPlan();

    public AlertSounds(FxPlayer player) {
        this.player = player;
    }

    /** Plays {@code kind}'s sound at client tick {@code now} unless it played within three seconds; whether it did. */
    public boolean play(NotificationFeed.Kind kind, long now) {
        AlertSoundPlan.Cue cue = plan.next(kind, now);
        if (cue == null) {
            return false;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(cue.sound()));
        if (sound == null) {
            return false;
        }
        player.local(sound, source(cue.category()), cue.volume(), cue.pitch());
        return true;
    }

    private static SoundSource source(AlertSoundPlan.Category category) {
        return switch (category) {
            case HOSTILE -> SoundSource.HOSTILE;
            case NEUTRAL -> SoundSource.NEUTRAL;
        };
    }
}
