package dev.strategos.mod.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.core.client.fx.ShotArc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws every shot in flight (CMB-04) as vanilla's arrow texture on two crossed quads along the arc's tangent, at the
 * frame's partial tick; no entity exists for it, so nothing is tracked, ticked or saved.
 */
public final class ShotArcRenderer {
    /** Half the arrow's length and half its width, in blocks: vanilla's 16 × 4 model units at its 0.05625 scale. */
    private static final float HALF_LENGTH = 0.45F;

    private static final float HALF_WIDTH = 0.1125F;
    // the shaft's part of the arrow texture, as vanilla's renderer maps it
    private static final float U_MAX = 0.5F;
    private static final float V_MAX = 0.15625F;

    private final ShotArcs arcs;

    public ShotArcRenderer(ShotArcs arcs) {
        this.arcs = arcs;
    }

    public void render(RenderLevelStageEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_ENTITIES)) {
            return;
        }
        long now = level.getGameTime();
        var flights = arcs.active(now);
        if (flights.isEmpty()) {
            return;
        }
        double time = now + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutoutNoCull(TippableArrowRenderer.NORMAL_ARROW_LOCATION);
        VertexConsumer quads = buffers.getBuffer(type);
        PoseStack.Pose pose = event.getPoseStack().last();
        Vec3 camera = event.getCamera().getPosition();
        double[] at = new double[3];
        double[] along = new double[3];
        for (ShotArcs.Flight flight : flights) {
            double f = flight.fraction(time);
            ShotArc arc = flight.arc();
            arc.position(f, at);
            arc.tangent(f, along);
            arrow(
                    quads,
                    pose,
                    (float) (at[0] - camera.x),
                    (float) (at[1] - camera.y),
                    (float) (at[2] - camera.z),
                    along);
        }
        buffers.endBatch(type);
    }

    /** Two quads through (x, y, z), both along {@code t}, one spread sideways and one across it. */
    private static void arrow(VertexConsumer q, PoseStack.Pose p, float x, float y, float z, double[] t) {
        float tx = (float) t[0];
        float ty = (float) t[1];
        float tz = (float) t[2];
        // sideways: the tangent crossed with up, or with east for a nearly vertical one
        float ux = tz;
        float uy = 0;
        float uz = -tx;
        float length = (float) Math.sqrt(ux * ux + uz * uz);
        if (length < 1e-4F) {
            ux = 0;
            uy = 0;
            uz = 1;
        } else {
            ux /= length;
            uz /= length;
        }
        // across: the tangent crossed with sideways
        float wx = ty * uz - tz * uy;
        float wy = tz * ux - tx * uz;
        float wz = tx * uy - ty * ux;
        quad(q, p, x, y, z, tx, ty, tz, ux, uy, uz, wx, wy, wz);
        quad(q, p, x, y, z, tx, ty, tz, wx, wy, wz, ux, uy, uz);
    }

    /** One quad centred on (x, y, z) spanning {@code t} by {@code s}, its normal {@code n}. */
    private static void quad(
            VertexConsumer q,
            PoseStack.Pose p,
            float x,
            float y,
            float z,
            float tx,
            float ty,
            float tz,
            float sx,
            float sy,
            float sz,
            float nx,
            float ny,
            float nz) {
        float lx = tx * HALF_LENGTH;
        float ly = ty * HALF_LENGTH;
        float lz = tz * HALF_LENGTH;
        float wx = sx * HALF_WIDTH;
        float wy = sy * HALF_WIDTH;
        float wz = sz * HALF_WIDTH;
        vertex(q, p, x - lx - wx, y - ly - wy, z - lz - wz, 0, 0, nx, ny, nz);
        vertex(q, p, x + lx - wx, y + ly - wy, z + lz - wz, U_MAX, 0, nx, ny, nz);
        vertex(q, p, x + lx + wx, y + ly + wy, z + lz + wz, U_MAX, V_MAX, nx, ny, nz);
        vertex(q, p, x - lx + wx, y - ly + wy, z - lz + wz, 0, V_MAX, nx, ny, nz);
    }

    private static void vertex(
            VertexConsumer q,
            PoseStack.Pose p,
            float x,
            float y,
            float z,
            float u,
            float v,
            float nx,
            float ny,
            float nz) {
        q.addVertex(p, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(p, nx, ny, nz);
    }
}
