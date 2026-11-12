package dev.strategos.mod.client.rts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.core.client.select.FocusRing;
import dev.strategos.core.client.select.NodeRing;
import dev.strategos.core.content.Blueprint;
import dev.strategos.core.content.Footprint;
import dev.strategos.core.protocol.BuildingState;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.entity.UnitEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * The ring on the target the selected units were told to attack (ORD-05): {@link FocusRing}'s band on the ground round
 * a unit, or {@link NodeRing}'s round a building's footprint, in attack red, while every selected unit holds that
 * target. A spread shows none. Solid quads, no particles, so a frozen frame draws it the same every time.
 */
final class FocusMarker {
    private static final float LIFT = 0.05F;
    private static final double BODY_RADIUS = 0.6;
    private static final int COLOUR = 0xFFE03020;

    private final RtsMode mode;

    FocusMarker(RtsMode mode) {
        this.mode = mode;
    }

    void render(RenderLevelStageEvent event) {
        if (!mode.active() || !event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_PARTICLES)) {
            return;
        }
        Integer target = mode.focusedTarget();
        ClientLevel level = Minecraft.getInstance().level;
        MatchJoin join = StrategosClient.match();
        if (target == null || level == null || join == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        PoseStack.Pose p = event.getPoseStack().last();
        Vec3 camera = event.getCamera().getPosition();
        Entity body = body(level, join, target);
        if (body != null) {
            float y = (float) (body.getY() - camera.y) + LIFT;
            for (FocusRing.Quad q : FocusRing.of(body.getX(), body.getZ(), BODY_RADIUS)) {
                vertex(quads, p, q.x0() - camera.x, y, q.z0() - camera.z);
                vertex(quads, p, q.x1() - camera.x, y, q.z1() - camera.z);
                vertex(quads, p, q.x2() - camera.x, y, q.z2() - camera.z);
                vertex(quads, p, q.x3() - camera.x, y, q.z3() - camera.z);
            }
        } else {
            for (NodeRing.Piece piece : building(target)) {
                int cx = join.originX() + piece.cellX();
                int cz = join.originZ() + piece.cellZ();
                if (!level.hasChunk(SectionPos.blockToSectionCoord(cx), SectionPos.blockToSectionCoord(cz))) {
                    continue;
                }
                float y = (float) (level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz) - camera.y) + LIFT;
                double x0 = join.originX() + piece.x0() - camera.x;
                double x1 = join.originX() + piece.x1() - camera.x;
                double z0 = join.originZ() + piece.z0() - camera.z;
                double z1 = join.originZ() + piece.z1() - camera.z;
                vertex(quads, p, x0, y, z0);
                vertex(quads, p, x0, y, z1);
                vertex(quads, p, x1, y, z1);
                vertex(quads, p, x1, y, z0);
            }
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    private static void vertex(VertexConsumer quads, PoseStack.Pose p, double x, float y, double z) {
        quads.addVertex(p, (float) x, y, (float) z).setColor(COLOUR);
    }

    /** The match's body of unit handle {@code target}, or null for a building or a unit not shown. */
    private static @Nullable Entity body(ClientLevel level, MatchJoin join, int target) {
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof UnitEntity unit
                    && unit.spawnData() != null
                    && unit.spawnData().matchId() == join.matchId()
                    && unit.spawnData().handle() == target) {
                return e;
            }
        }
        return null;
    }

    /** The band round building {@code target}'s footprint; empty if the model does not list it. */
    private static List<NodeRing.Piece> building(int target) {
        for (BuildingState.Entry b : StrategosClient.model().buildings()) {
            if (b.handle() == target) {
                Blueprint bp = StrategosClient.templates().blueprint(b.template());
                return bp == null ? List.of() : NodeRing.of(Footprint.of(bp, b.x(), b.z(), b.rotation()));
            }
        }
        return List.of();
    }
}
