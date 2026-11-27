package dev.strategos.mod.client.mixin;

import dev.strategos.mod.client.rts.OrthoView;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The optional orthographic RTS view (CAM-06, D-09): at the head of {@code GameRenderer#getProjectionMatrix(double)}
 * (GameRenderer.java:980) the matrix is replaced by an orthographic one while {@link OrthoView} is active; otherwise
 * the vanilla method runs untouched. Client only.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    public abstract float getDepthFar();

    @Inject(method = "getProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void strategos$orthographic(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        Matrix4f ortho = OrthoView.projection(getDepthFar());
        if (ortho != null) {
            cir.setReturnValue(ortho);
        }
    }
}
