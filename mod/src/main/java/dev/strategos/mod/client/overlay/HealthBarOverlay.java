package dev.strategos.mod.client.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.core.client.hud.HealthBars;
import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.client.select.Selection;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.shared.Handle;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

/**
 * Health bars over the bodies (CMB-06, 04 §13.6): each frame the bodies of the joined match, interpolated to the
 * frame, and their hit points from {@code unit_hud} become {@link HealthBars}' frame data, then one batch of quads
 * facing the camera, a dark back and the fill, at {@code AFTER_PARTICLES}. Hidden with the GUI (F1). It times its work.
 */
public final class HealthBarOverlay {
    private static final float THICK = 0.12F;
    private static final int BACK = 0xC0101418;

    private final ClientMatchModel model;
    private final RtsMode rts;
    private long nanos;
    private int drawn;

    public HealthBarOverlay(ClientMatchModel model, RtsMode rts) {
        this.model = model;
        this.rts = rts;
    }

    public void register() {
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, this::onStage);
    }

    /** Bars the last frame drew, and the nanoseconds it took, for scenarios. */
    public int drawn() {
        return drawn;
    }

    public long nanos() {
        return nanos;
    }

    private void onStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        MatchJoin join = StrategosClient.match();
        if (!event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_PARTICLES)
                || join == null
                || mc.level == null
                || mc.options.hideGui) {
            return;
        }
        long start = System.nanoTime();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Selection selection = rts.selection();
        List<HealthBars.Unit> units = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof UnitEntity body && !body.isDeadOrDying()) {
                UnitSpawnData data = body.spawnData();
                if (data != null && data.matchId() == join.matchId()) {
                    Vec3 at = body.getPosition(partial);
                    units.add(new HealthBars.Unit(
                            at.x,
                            at.y,
                            at.z,
                            body.getBbHeight(),
                            body.getBbWidth(),
                            selection.contains(data.handle()),
                            model.health(Handle.index(data.handle()))));
                }
            }
        }
        List<HealthBars.Bar> bars = HealthBars.extract(units);
        drawn = bars.size();
        if (!bars.isEmpty()) {
            draw(event, bars);
        }
        nanos = System.nanoTime() - start;
    }

    private static void draw(RenderLevelStageEvent event, List<HealthBars.Bar> bars) {
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        PoseStack.Pose p = event.getPoseStack().last();
        Camera camera = event.getCamera();
        Vec3 c = camera.getPosition();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        Vector3f look = camera.getLookVector();
        for (HealthBars.Bar b : bars) {
            float x = (float) (b.x() - c.x);
            float y = (float) (b.y() - c.y);
            float z = (float) (b.z() - c.z);
            float half = (float) b.width() / 2;
            quad(quads, p, x, y, z, left, up, look, -half, half, BACK, 0);
            quad(quads, p, x, y, z, left, up, look, -half, -half + (float) (b.width() * b.fill()), b.argb(), 0.01F);
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    /** A quad from {@code from} to {@code to} along the camera's right, {@link #THICK} tall, nudged toward the camera. */
    private static void quad(
            VertexConsumer quads,
            PoseStack.Pose p,
            float x,
            float y,
            float z,
            Vector3f left,
            Vector3f up,
            Vector3f look,
            float from,
            float to,
            int argb,
            float nudge) {
        // the camera's right is minus its left; the nudge goes against the look, toward the camera, so the fill is
        // drawn in front of its back
        float rx = -left.x();
        float ry = -left.y();
        float rz = -left.z();
        float ux = up.x() * THICK / 2;
        float uy = up.y() * THICK / 2;
        float uz = up.z() * THICK / 2;
        float nx = -look.x() * nudge;
        float ny = -look.y() * nudge;
        float nz = -look.z() * nudge;
        quads.addVertex(p, x + rx * from - ux + nx, y + ry * from - uy + ny, z + rz * from - uz + nz)
                .setColor(argb);
        quads.addVertex(p, x + rx * to - ux + nx, y + ry * to - uy + ny, z + rz * to - uz + nz)
                .setColor(argb);
        quads.addVertex(p, x + rx * to + ux + nx, y + ry * to + uy + ny, z + rz * to + uz + nz)
                .setColor(argb);
        quads.addVertex(p, x + rx * from + ux + nx, y + ry * from + uy + ny, z + rz * from + uz + nz)
                .setColor(argb);
    }
}
