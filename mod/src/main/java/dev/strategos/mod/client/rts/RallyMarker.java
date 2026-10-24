package dev.strategos.mod.client.rts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.mod.client.StrategosClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * The selected building's rally point (PROD-03.3): a white pole with a flag in the owner's colour and a square on the
 * ground, where {@code building_state} says new units go, for as long as the building is selected. Solid quads, no
 * particles, so a frozen frame draws it the same every time.
 */
final class RallyMarker {
    private static final float POLE = 0.06F;
    private static final float HEIGHT = 2.2F;
    private static final float FLAG = 0.7F;
    private static final float FLAG_DROP = 0.45F;
    private static final float SQUARE = 0.5F;
    private static final float LIFT = 0.05F;
    private static final int POLE_COLOUR = 0xFFF0F0F0;

    private final RtsMode mode;
    private final HudTheme theme = HudTheme.DEFAULT;

    RallyMarker(RtsMode mode) {
        this.mode = mode;
    }

    /** The marker drawn this frame, world x, ground y and z; null if none. */
    double @Nullable [] shown() {
        BuildingState.Entry b = selected();
        ClientLevel level = Minecraft.getInstance().level;
        MatchJoin join = StrategosClient.match();
        if (b == null || level == null || join == null) {
            return null;
        }
        int x = join.originX() + b.rallyX();
        int z = join.originZ() + b.rallyZ();
        if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
            return null;
        }
        return new double[] {x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5};
    }

    void render(RenderLevelStageEvent event) {
        if (!mode.active() || !event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_PARTICLES)) {
            return;
        }
        double[] at = shown();
        BuildingState.Entry b = selected();
        if (at == null || b == null) {
            return;
        }
        int colour = (theme.faction(b.faction()) & 0x00FFFFFF) | 0xFF000000;
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        PoseStack.Pose p = event.getPoseStack().last();
        Vec3 camera = event.getCamera().getPosition();
        float x = (float) (at[0] - camera.x);
        float y = (float) (at[1] - camera.y) + LIFT;
        float z = (float) (at[2] - camera.z);
        // the ground square, the pole as two crossed faces, and the flag both ways
        quad(
                quads,
                p,
                x - SQUARE,
                y,
                z - SQUARE,
                x - SQUARE,
                y,
                z + SQUARE,
                x + SQUARE,
                y,
                z + SQUARE,
                x + SQUARE,
                y,
                z - SQUARE,
                colour);
        quad(quads, p, x - POLE, y, z, x + POLE, y, z, x + POLE, y + HEIGHT, z, x - POLE, y + HEIGHT, z, POLE_COLOUR);
        quad(quads, p, x, y, z - POLE, x, y, z + POLE, x, y + HEIGHT, z + POLE, x, y + HEIGHT, z - POLE, POLE_COLOUR);
        float top = y + HEIGHT;
        quad(quads, p, x, top, z, x + FLAG, top, z, x + FLAG, top - FLAG_DROP, z, x, top - FLAG_DROP, z, colour);
        quad(quads, p, x, top - FLAG_DROP, z, x + FLAG, top - FLAG_DROP, z, x + FLAG, top, z, x, top, z, colour);
        buffers.endBatch(RenderType.debugQuads());
    }

    /** The selected building, if it is this player's and has a rally point. */
    private BuildingState.@Nullable Entry selected() {
        Integer handle = mode.selection().building();
        MatchJoin join = StrategosClient.match();
        if (handle == null || join == null) {
            return null;
        }
        for (BuildingState.Entry b : StrategosClient.model().buildings()) {
            if (b.handle() == handle && b.rally() && b.faction() == join.slot().value()) {
                return b;
            }
        }
        return null;
    }

    private static void quad(
            VertexConsumer q,
            PoseStack.Pose p,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            int argb) {
        q.addVertex(p, x0, y0, z0).setColor(argb);
        q.addVertex(p, x1, y1, z1).setColor(argb);
        q.addVertex(p, x2, y2, z2).setColor(argb);
        q.addVertex(p, x3, y3, z3).setColor(argb);
    }
}
