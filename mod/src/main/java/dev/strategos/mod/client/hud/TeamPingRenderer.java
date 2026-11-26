package dev.strategos.mod.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.mod.client.StrategosClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draws a ground cross at each active allied marker whose terrain the client has loaded. */
public final class TeamPingRenderer {
    private static final float ARM = 2.5F;
    private static final float WIDTH = 0.22F;

    private TeamPingRenderer() {}

    public static void render(RenderLevelStageEvent event) {
        if (!event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_PARTICLES)) return;
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var join = StrategosClient.match();
        var pings = StrategosClient.teamPings().active();
        if (level == null || join == null || pings.isEmpty() || minecraft.options.hideGui) return;

        var buffers = minecraft.renderBuffers().bufferSource();
        var quads = buffers.getBuffer(RenderType.debugQuads());
        var pose = event.getPoseStack().last();
        var camera = event.getCamera().getPosition();
        for (var ping : pings) {
            int blockX = join.originX() + ping.x();
            int blockZ = join.originZ() + ping.z();
            if (!level.getChunkSource().hasChunk(blockX >> 4, blockZ >> 4)) continue;
            float x = (float) (blockX + 0.5 - camera.x);
            float z = (float) (blockZ + 0.5 - camera.z);
            float y = (float) (level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) - camera.y) + 0.12F;
            quad(quads, pose, x - ARM, x + ARM, y, z - WIDTH, z + WIDTH);
            quad(quads, pose, x - WIDTH, x + WIDTH, y, z - ARM, z - WIDTH);
            quad(quads, pose, x - WIDTH, x + WIDTH, y, z + WIDTH, z + ARM);
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, float x0, float x1, float y, float z0, float z1) {
        out.addVertex(pose, x0, y, z0).setColor(TeamPingModel.COLOUR);
        out.addVertex(pose, x0, y, z1).setColor(TeamPingModel.COLOUR);
        out.addVertex(pose, x1, y, z1).setColor(TeamPingModel.COLOUR);
        out.addVertex(pose, x1, y, z0).setColor(TeamPingModel.COLOUR);
    }
}
