package dev.strategos.mod.client.audio;

import dev.strategos.core.client.audio.SoundBudget;
import dev.strategos.core.client.audio.Sounds;
import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.protocol.Feedback;
import dev.strategos.core.protocol.Fx;
import dev.strategos.core.protocol.FxKind;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.mod.client.rts.RtsMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Plays the match's sounds on this client (AUD-01): an {@code fx} event where it happened, loud enough to carry from
 * the RTS camera's height and at most six of a kind a tick; an order's acknowledgement as an interface sound. The
 * world's edits (AUD-04) show as vanilla shows them: a crack through {@code ClientLevel#destroyBlockProgress}
 * (ClientLevel.java:686) under a breaker id of the block's own, a break's particles through {@code
 * addDestroyBlockEffect} (962) and its sound type's break sound, a placement's place sound, both at vanilla's volume
 * and pitch for blocks. Nothing here reaches the server, and the server plays none of these.
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
            if (e.kind().carriesData()) {
                block(level, join, e, rig);
                continue;
            }
            if (e.kind() == FxKind.FLAME) {
                flames(level, join, e);
            }
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

    private void block(ClientLevel level, MatchJoin join, Fx.Event e, @Nullable CameraRig rig) {
        BlockPos pos = BlockPos.containing(
                join.originX() + e.x8() / 8.0, join.originY() + e.y8() / 8.0, join.originZ() + e.z8() / 8.0);
        if (e.kind() == FxKind.BLOCK_CRACK) {
            level.destroyBlockProgress(breaker(pos), pos, e.data());
            return;
        }
        BlockState state = Block.stateById(e.data());
        if (e.kind() == FxKind.BLOCK_BREAK) {
            level.destroyBlockProgress(breaker(pos), pos, -1); // the crack goes with the block
        }
        if (!budget.take(e.kind(), level.getGameTime())) {
            return;
        }
        SoundType type = state.getSoundType();
        if (e.kind() == FxKind.BLOCK_BREAK) {
            level.addDestroyBlockEffect(pos, state);
        }
        float base = (type.getVolume() + 1.0F) / 2.0F; // vanilla's for a broken or placed block
        float volume = rig == null
                ? base
                : Sounds.volume(base, rig.height(), rig.pose().pitch());
        level.playLocalSound(
                pos,
                e.kind() == FxKind.BLOCK_BREAK ? type.getBreakSound() : type.getPlaceSound(),
                SoundSource.BLOCKS,
                volume,
                type.getPitch() * 0.8F,
                false);
        played++;
    }

    /**
     * A torch's flames on a building's block (CMB-03): flame particles and smoke over it, forced, since the RTS camera
     * is further than vanilla's 32 blocks for plain particles; never a fire block.
     */
    private static void flames(ClientLevel level, MatchJoin join, Fx.Event e) {
        double x = join.originX() + e.x8() / 8.0 + 0.5;
        double y = join.originY() + e.y8() / 8.0 + 0.5;
        double z = join.originZ() + e.z8() / 8.0 + 0.5;
        var rng = level.getRandom();
        for (int k = 0; k < 6; k++) {
            level.addParticle(
                    ParticleTypes.FLAME,
                    true,
                    x + (rng.nextDouble() - 0.5) * 0.9,
                    y + (rng.nextDouble() - 0.5) * 0.9,
                    z + (rng.nextDouble() - 0.5) * 0.9,
                    0,
                    0.02,
                    0);
        }
        level.addParticle(ParticleTypes.LARGE_SMOKE, true, x, y + 0.6, z, 0, 0.05, 0);
    }

    /** A breaker id of the block's own, negative so it never meets an entity's: one crack per block. */
    private static int breaker(BlockPos pos) {
        return Integer.MIN_VALUE | (Long.hashCode(pos.asLong()) & Integer.MAX_VALUE);
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
