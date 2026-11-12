package dev.strategos.mod.client.fog;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.core.client.fog.ShroudExtract;
import dev.strategos.core.client.model.ClientMatchModel;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.mod.client.StrategosClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The fog shroud (FOW-07, 04 §13.6): translucent black/dim quads on the terrain from the client's vision grid, per
 * 16×16 page, rebuilt only when that page's cells change. Drawn at {@code AFTER_TRANSLUCENT_BLOCKS} with
 * {@link RenderType#translucent()} so shader packs keep the pass. No-ops when there is no joined match or no vision.
 * M6 solo fog only — explored / visible / unexplored from {@link ClientMatchModel}; M8 terrain secrecy and mirages stay
 * elsewhere.
 */
public final class ShroudRenderer {
    private static final float LIFT = 0.08F;
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("block/white_wool");

    private final ClientMatchModel model;
    private Page[] pages = new Page[0];
    private int cellsX;
    private int cellsZ;
    private int pagesX;
    private int pagesZ;
    private int lastRevision = -1;

    public ShroudRenderer(ClientMatchModel model) {
        this.model = model;
    }

    public void register() {
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, this::onStage);
    }

    private void onStage(RenderLevelStageEvent event) {
        if (!event.getStage().equals(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        MatchJoin join = StrategosClient.match();
        if (level == null || join == null || model.visionCellsX() <= 0 || model.visionCellsZ() <= 0) {
            return;
        }
        ensurePages();
        int revision = model.visionRevision();
        if (revision != lastRevision) {
            rebuildDirty(level, join);
            lastRevision = revision;
        }
        draw(event, join);
    }

    private void ensurePages() {
        int cx = model.visionCellsX();
        int cz = model.visionCellsZ();
        if (cx == cellsX && cz == cellsZ && pages.length > 0) {
            return;
        }
        cellsX = cx;
        cellsZ = cz;
        pagesX = ShroudExtract.pages(cx);
        pagesZ = ShroudExtract.pages(cz);
        pages = new Page[pagesX * pagesZ];
        for (int i = 0; i < pages.length; i++) {
            pages[i] = new Page();
        }
        lastRevision = -1;
    }

    private void rebuildDirty(ClientLevel level, MatchJoin join) {
        long[] visible = model.visibleBits();
        long[] explored = model.exploredBits();
        int i = 0;
        for (int pz = 0; pz < pagesZ; pz++) {
            for (int px = 0; px < pagesX; px++) {
                Page page = pages[i++];
                if (!ShroudExtract.extractPage(visible, explored, cellsX, cellsZ, px, pz, page.mesh)) {
                    continue;
                }
                bakeHeights(level, join, page);
            }
        }
    }

    private static void bakeHeights(ClientLevel level, MatchJoin join, Page page) {
        ShroudExtract.PageMesh mesh = page.mesh;
        int n = mesh.quads() * 4;
        if (page.y.length < n) {
            page.y = new float[Math.max(n, 64)];
        }
        for (int q = 0; q < mesh.quads(); q++) {
            for (int c = 0; c < 4; c++) {
                int bx = join.originX() + (int) Math.floor(mesh.x(q, c));
                int bz = join.originZ() + (int) Math.floor(mesh.z(q, c));
                float y;
                if (level.hasChunk(SectionPos.blockToSectionCoord(bx), SectionPos.blockToSectionCoord(bz))) {
                    y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) + LIFT;
                } else {
                    y = join.originY() + LIFT;
                }
                page.y[q * 4 + c] = y;
            }
        }
    }

    private void draw(RenderLevelStageEvent event, MatchJoin join) {
        int total = 0;
        for (Page page : pages) {
            total += page.mesh.quads();
        }
        if (total == 0) {
            return;
        }
        TextureAtlasSprite sprite = mcAtlas().getSprite(WHITE);
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float u1 = sprite.getU1();
        float v1 = sprite.getV1();
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = RenderType.translucent();
        VertexConsumer quads = buffers.getBuffer(type);
        PoseStack.Pose pose = event.getPoseStack().last();
        Vec3 camera = event.getCamera().getPosition();
        double ox = join.originX() - camera.x;
        double oz = join.originZ() - camera.z;
        double oy = -camera.y;
        for (Page page : pages) {
            ShroudExtract.PageMesh mesh = page.mesh;
            for (int q = 0; q < mesh.quads(); q++) {
                vertex(quads, pose, ox + mesh.x(q, 0), oy + page.y[q * 4], oz + mesh.z(q, 0), u0, v0, mesh.alpha(q, 0));
                vertex(
                        quads,
                        pose,
                        ox + mesh.x(q, 1),
                        oy + page.y[q * 4 + 1],
                        oz + mesh.z(q, 1),
                        u1,
                        v0,
                        mesh.alpha(q, 1));
                vertex(
                        quads,
                        pose,
                        ox + mesh.x(q, 2),
                        oy + page.y[q * 4 + 2],
                        oz + mesh.z(q, 2),
                        u1,
                        v1,
                        mesh.alpha(q, 2));
                vertex(
                        quads,
                        pose,
                        ox + mesh.x(q, 3),
                        oy + page.y[q * 4 + 3],
                        oz + mesh.z(q, 3),
                        u0,
                        v1,
                        mesh.alpha(q, 3));
            }
        }
        buffers.endBatch(type);
    }

    private static TextureAtlas mcAtlas() {
        return Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
    }

    private static void vertex(
            VertexConsumer quads, PoseStack.Pose pose, double x, double y, double z, float u, float v, float alpha) {
        if (alpha <= 0) {
            // Still emit so the quad stays planar; fully clear corners keep a = 0.
            quads.addVertex(pose, (float) x, (float) y, (float) z)
                    .setColor(0, 0, 0, 0)
                    .setUv(u, v)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(pose, 0, 1, 0);
            return;
        }
        int a = Math.min(255, Math.max(0, Math.round(alpha * 255)));
        quads.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(0, 0, 0, a)
                .setUv(u, v)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }

    private static final class Page {
        final ShroudExtract.PageMesh mesh = new ShroudExtract.PageMesh();
        float[] y = new float[0];
    }
}
