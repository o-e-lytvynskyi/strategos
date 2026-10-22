package dev.strategos.mod.mixin;

import dev.strategos.mod.server.world.BlockChangeRouter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sees every block change of a loaded chunk, direct chunk writes included (fact 12): at the return of {@code
 * LevelChunk#setBlockState} (LevelChunk.java:239), a change that happened (the previous state returned) goes to {@link
 * BlockChangeRouter}, and nothing else.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void strategos$changed(
            BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
        BlockState previous = cir.getReturnValue();
        if (previous != null) {
            BlockChangeRouter.onChanged((LevelChunk) (Object) this, pos, previous, state);
        }
    }
}
