package dev.strategos.mod.client.audio;

import dev.strategos.core.client.audio.SoundBudget;
import dev.strategos.core.client.audio.Sounds;
import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Fx;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.mod.client.rts.RtsMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

/**
 * Plays the match's sounds on this client (AUD-01): an {@code fx} event where it happened, loud enough to carry from
 * the RTS camera's height and at most six of a kind a tick; an order's acknowledgement as an interface sound. Nothing
 * here reaches the server, and the server plays none of these.
 */
public final class FxPlayer {
    private final RtsMode rts;
    private final SoundBudget budget = new SoundBudget();
    private int played;

    public FxPlayer(RtsMode rts) {
        this.rts = rts;
    }

    public void accept(Fx fx, MatchJoin join) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || fx.matchId() != join.matchId()) {
            return;
        }
        CameraRig rig = rts.active() ? rts.rig() : null;
        for (Fx.Event e : fx.events()) {
            Sounds.Cue cue = Sounds.of(e.kind());
            SoundEvent sound = sound(cue);
            if (sound == null || !budget.take(e.kind(), level.getGameTime())) {
                continue;
            }
            float volume = rig == null
                    ? cue.volume()
                    : Sounds.volume(cue, rig.height(), rig.pose().pitch());
            level.playLocalSound(
                    join.originX() + e.x8() / 8.0,
                    join.originY() + e.y8() / 8.0,
                    join.originZ() + e.z8() / 8.0,
                    sound,
                    source(cue),
                    volume,
                    cue.pitch(),
                    false);
            played++;
        }
    }

    /** An order's acknowledgement, accepted or refused. */
    public void feedback(Feedback feedback) {
        Sounds.Cue cue = Sounds.of(feedback.outcome());
        SoundEvent sound = sound(cue);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, cue.pitch(), cue.volume()));
            played++;
        }
    }

    /** How many sounds this client started, for the scenario harness. */
    public int played() {
        return played;
    }

    private static @Nullable SoundEvent sound(Sounds.Cue cue) {
        return BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(cue.sound()));
    }

    private static SoundSource source(Sounds.Cue cue) {
        return switch (cue.category()) {
            case UI -> SoundSource.MASTER;
            case UNITS -> SoundSource.NEUTRAL;
        };
    }
}
