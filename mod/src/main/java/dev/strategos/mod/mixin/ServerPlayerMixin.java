package dev.strategos.mod.mixin;

import dev.strategos.mod.server.commander.CommanderGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla player hooks only delegate to the server-owned commander policy. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void strategos$denyAttackCamera(Entity target, CallbackInfo ci) {
        if (CommanderGuard.deniesAttackCamera((ServerPlayer) (Object) this)) ci.cancel();
    }

    @Inject(method = "broadcastToPlayer", at = @At("RETURN"), cancellable = true)
    private void strategos$hidePrivateBody(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !CommanderGuard.allowsBody((ServerPlayer) (Object) this, viewer)) {
            cir.setReturnValue(false);
        }
    }
}
