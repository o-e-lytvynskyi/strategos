package dev.strategos.mod.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

/**
 * The bodies' level of detail on vanilla (M4 T3, M3 §1.6 row 77): the armour and held items, drawn as many render
 * types each, cost a crowd 8.9 ms a frame and an outlined hundred 37 ms more. Without Sodium, which batches them, a body
 * beyond {@value #FAR} blocks of the camera, or past the first {@value #MOST} drawn this frame, draws without them; a
 * selection outline is the body's alone with either. Client thread only; the frame count starts again at each {@code
 * RenderFrameEvent.Pre}.
 */
public final class UnitLod {
    /** Beyond the RTS camera's farthest view of its focus: the looks at 60 blocks keep their roles (ART-01.3). */
    static final double FAR = 128;

    static final int MOST = 50;

    private int drawn;
    private @Nullable Boolean sodium;

    /** A new frame: nobody drawn yet. */
    void frame() {
        drawn = 0;
    }

    /** Whether the body at {@code at}, the next drawn this frame, draws its armour and held items. */
    boolean detailed(Vec3 at, Vec3 camera) {
        drawn++;
        Boolean batched = sodium;
        if (batched == null) {
            batched = ModList.get().isLoaded("sodium");
            sodium = batched;
        }
        return batched || (drawn <= MOST && at.distanceToSqr(camera) <= FAR * FAR); // Sodium draws them cheaply
    }

    /**
     * {@code outline} for the first buffer asked for, the body's own, and {@code plain} for every other: the layers
     * then draw unoutlined, so the outline pass meets one render type a body, not one per texture.
     */
    static MultiBufferSource bodyOnly(MultiBufferSource outline, MultiBufferSource plain) {
        return new MultiBufferSource() {
            private boolean body = true;

            @Override
            public VertexConsumer getBuffer(RenderType type) {
                if (body) {
                    body = false;
                    return outline.getBuffer(type);
                }
                return plain.getBuffer(type);
            }
        };
    }
}
