package dev.strategos.mod.client.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.core.client.hud.HudTheme;
import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.UnitSummary;
import dev.strategos.core.shared.Handle;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.entity.UnitEntity;
import dev.strategos.mod.entity.UnitSpawnData;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
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
import net.neoforged.neoforge.common.NeoForge;

/**
 * Far-unit markers (NET-08, 04 §12.4): a unit the faction knows from {@code unit_summary} whose body this client does
 * not track, beyond the tracking range, is a square of its faction's colour on the ground, so zooming out never makes
 * an army vanish. Each frame it extracts the markers from the model and the tracked bodies, then draws them in one
 * batch at {@code AFTER_PARTICLES}; columns whose chunk is not loaded have no ground to put a marker on.
 */
public final class FarMarkers {
    private static final float HALF = 0.6F;
    private static final float LIFT = 0.06F;

    private final ClientMatchModel model;
    private final HudTheme theme = HudTheme.DEFAULT;
    private IntOpenHashSet drawn = new IntOpenHashSet();

    /** One marker: the unit's index, world position and colour. */
    private record Marker(int index, double x, double y, double z, int argb) {}

    public FarMarkers(ClientMatchModel model) {
        this.model = model;
    }

    public void register() {
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, this::onStage);
    }

    /** The indices of the units the last frame drew as markers, for scenarios. */
    public IntOpenHashSet drawn() {
        return drawn;
    }

    private void onStage(RenderLevelStageEvent event) {
        if (!event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_PARTICLES)) {
            return;
        }
        List<Marker> markers = extract();
        IntOpenHashSet indices = new IntOpenHashSet(markers.size());
        markers.forEach(m -> indices.add(m.index()));
        drawn = indices;
        if (markers.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        PoseStack.Pose p = event.getPoseStack().last();
        Vec3 camera = event.getCamera().getPosition();
        for (Marker m : markers) {
            float x = (float) (m.x() - camera.x);
            float y = (float) (m.y() - camera.y) + LIFT;
            float z = (float) (m.z() - camera.z);
            quads.addVertex(p, x - HALF, y, z - HALF).setColor(m.argb());
            quads.addVertex(p, x - HALF, y, z + HALF).setColor(m.argb());
            quads.addVertex(p, x + HALF, y, z + HALF).setColor(m.argb());
            quads.addVertex(p, x + HALF, y, z - HALF).setColor(m.argb());
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    private List<Marker> extract() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        MatchJoin join = StrategosClient.match();
        List<Marker> out = new ArrayList<>();
        if (level == null || join == null || model.units().isEmpty()) {
            return out;
        }
        IntOpenHashSet tracked = new IntOpenHashSet();
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof UnitEntity body) {
                UnitSpawnData data = body.spawnData();
                if (data != null && data.matchId() == join.matchId()) {
                    tracked.add(Handle.index(data.handle()));
                }
            }
        }
        for (UnitSummary.Entry u : model.units()) {
            if (tracked.contains(u.index())) {
                continue;
            }
            double x = join.originX() + u.x();
            double z = join.originZ() + u.z();
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            if (!level.hasChunk(SectionPos.blockToSectionCoord(bx), SectionPos.blockToSectionCoord(bz))) {
                continue;
            }
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
            out.add(new Marker(u.index(), x, ground, z, (theme.faction(u.faction()) & 0x00FFFFFF) | 0xE0000000));
        }
        return out;
    }
}
