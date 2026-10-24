package dev.strategos.mod.client.rts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.client.select.NodeRing;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.protocol.DepositState;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.mod.client.StrategosClient;
import java.util.List;
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
 * The selected deposit's marker (ART-02): {@link NodeRing}'s band round its footprint in the HUD's accent, each piece on
 * its own cell's ground, for as long as the deposit is selected.
 */
final class NodeMarker {
    private static final float LIFT = 0.05F;

    private final RtsMode mode;
    private final HudTheme theme = HudTheme.DEFAULT;

    NodeMarker(RtsMode mode) {
        this.mode = mode;
    }

    /** The band's pieces drawn this frame, match-local; empty with no deposit selected. */
    List<NodeRing.Piece> shown() {
        DepositState.Entry e = selected();
        Blueprint bp = e == null ? null : StrategosClient.templates().blueprint(e.template());
        return e == null || bp == null ? List.of() : NodeRing.of(Footprint.of(bp, e.x(), e.z(), 0));
    }

    void render(RenderLevelStageEvent event) {
        if (!mode.active() || !event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_PARTICLES)) {
            return;
        }
        List<NodeRing.Piece> ring = shown();
        ClientLevel level = Minecraft.getInstance().level;
        MatchJoin join = StrategosClient.match();
        if (ring.isEmpty() || level == null || join == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        PoseStack.Pose p = event.getPoseStack().last();
        Vec3 camera = event.getCamera().getPosition();
        int colour = theme.accent();
        for (NodeRing.Piece piece : ring) {
            int cx = join.originX() + piece.cellX();
            int cz = join.originZ() + piece.cellZ();
            if (!level.hasChunk(SectionPos.blockToSectionCoord(cx), SectionPos.blockToSectionCoord(cz))) {
                continue;
            }
            float y = (float) (level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz) - camera.y) + LIFT;
            float x0 = (float) (join.originX() + piece.x0() - camera.x);
            float x1 = (float) (join.originX() + piece.x1() - camera.x);
            float z0 = (float) (join.originZ() + piece.z0() - camera.z);
            float z1 = (float) (join.originZ() + piece.z1() - camera.z);
            quads.addVertex(p, x0, y, z0).setColor(colour);
            quads.addVertex(p, x0, y, z1).setColor(colour);
            quads.addVertex(p, x1, y, z1).setColor(colour);
            quads.addVertex(p, x1, y, z0).setColor(colour);
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    /** The selected deposit, while the model still lists it. */
    private DepositState.@Nullable Entry selected() {
        Integer node = mode.selection().deposit();
        if (node == null) {
            return null;
        }
        for (DepositState.Entry e : StrategosClient.model().deposits()) {
            if (e.node() == node) {
                return e;
            }
        }
        return null;
    }
}
