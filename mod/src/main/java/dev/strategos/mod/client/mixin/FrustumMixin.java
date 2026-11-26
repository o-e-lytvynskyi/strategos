package dev.strategos.mod.client.mixin;

import dev.strategos.mod.client.rts.OrthoView;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The orthographic view's other half (CAM-06): {@code Frustum#offsetToFullyIncludeCameraCube(int)} (Frustum.java:33)
 * widens the culling frustum in a loop that never ends under an orthographic matrix, so while {@link OrthoView} is
 * active the frustum is returned as it is. Its caller is {@code LevelRenderer.offsetFrustum} (LevelRenderer.java:875).
 * Client only.
 */
@Mixin(Frustum.class)
public abstract class FrustumMixin {
    @Inject(method = "offsetToFullyIncludeCameraCube", at = @At("HEAD"), cancellable = true)
    private void strategos$skipLoop(int offset, CallbackInfoReturnable<Frustum> cir) {
        if (OrthoView.active()) {
            cir.setReturnValue((Frustum) (Object) this);
        }
    }
}
