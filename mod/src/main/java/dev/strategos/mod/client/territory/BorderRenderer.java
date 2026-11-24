package dev.strategos.mod.client.territory;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.strategos.core.client.territory.BorderGeometry;
import dev.strategos.core.client.territory.ClientTerritory;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.shared.TerritoryGrid;
import dev.strategos.core.sim.Allies;
import dev.strategos.mod.client.StrategosClient;
import java.util.Arrays;
import java.util.BitSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The territory borders on the terrain (TERR-03, M9 spec §2.11): the quads of {@link BorderGeometry} added to the chunk
 * sections' own translucent geometry, so they are culled, sorted and lit with the terrain, and rebuilt only with it. The
 * event fires on the main thread, which copies the section's quads out of the client's territory into an immutable
 * array; the renderer, which runs on a chunk-build thread, reads nothing else. A completed territory version dirties
 * the sections its changed tiles touch ({@link BorderGeometry#sectionsTouched}) over the match's height band. Match
 * space is not section-aligned in the world, so a world section is cut from the match-local columns it overlaps.
 *
 * <p>The same sections are dirtied once more {@value #ECHO_TICKS} client ticks later. Under Sodium some first marks were
 * lost: a section whose build had already taken the old borders was uploaded with them and stayed so (M9 T19's
 * goldens showed gaps that a full re-mesh closed); the echo re-meshes those once the in-flight builds are done.
 */
public final class BorderRenderer {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("block/white_wool");
    static final int ECHO_TICKS = 10;

    private BorderRenderer() {}

    /** Subscribes to the section geometry event (the game bus, the client's only). */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(AddSectionGeometryEvent.class, BorderRenderer::onSection);
        NeoForge.EVENT_BUS.addListener(
                ClientTickEvent.Post.class, e -> StrategosClient.borderEcho().tick());
    }

    /** What a chunk-build thread draws: quads of 12 section-relative floats and one ARGB each, and the sprite's UV. */
    private static final class Quads {
        final float[] coords;
        final int[] colours;
        final float u;
        final float v;

        Quads(float[] coords, int[] colours, float u, float v) {
            this.coords = coords;
            this.colours = colours;
            this.u = u;
            this.v = v;
        }
    }

    private static void onSection(AddSectionGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        MatchJoin join = StrategosClient.match();
        TerritoryGrid grid = StrategosClient.model().territory().grid();
        if (join == null || grid == null || mc.level == null || !mc.level.equals(event.getLevel())) {
            return;
        }
        BlockPos origin = event.getSectionOrigin();
        if (origin.getY() + 15 < join.minY() || origin.getY() > join.maxY()) {
            return;
        }
        int localX = origin.getX() - join.originX();
        int localZ = origin.getZ() - join.originZ();
        ClientLevel level = mc.level;
        Allies allies = Allies.sameFaction(join.factions());
        int viewer = join.slot().value();
        BorderGeometry.Buffer buffer = new BorderGeometry.Buffer();
        float[] coords = new float[12 * 32];
        int[] colours = new int[32];
        int n = 0;
        // the match-local columns this world section overlaps: one, or two or four when the match is not aligned
        for (int lz = Math.floorDiv(localZ, 16); lz <= Math.floorDiv(localZ + 15, 16); lz++) {
            for (int lx = Math.floorDiv(localX, 16); lx <= Math.floorDiv(localX + 15, 16); lx++) {
                if (lx < 0
                        || lz < 0
                        || lx >= BorderGeometry.sectionsFor(grid.tilesX())
                        || lz >= BorderGeometry.sectionsFor(grid.tilesZ())) {
                    continue;
                }
                BorderGeometry.extract(
                        grid,
                        lx,
                        lz,
                        viewer,
                        f -> allies.allied(viewer, f),
                        (x, z) -> floor(level, join, x, z),
                        buffer);
                for (int q = 0; q < buffer.count(); q++) {
                    // the quad belongs to the section holding its first corner, at its own height
                    float dx = lx * 16 + buffer.x(q, 0) - localX;
                    float dz = lz * 16 + buffer.z(q, 0) - localZ;
                    float dy = buffer.y(q, 0) - origin.getY();
                    if (dx < 0 || dx >= 16 || dz < 0 || dz >= 16 || dy < 0 || dy >= 16) {
                        continue;
                    }
                    if (n == colours.length) {
                        colours = Arrays.copyOf(colours, n * 2);
                        coords = Arrays.copyOf(coords, n * 24);
                    }
                    for (int c = 0; c < 4; c++) {
                        coords[n * 12 + c * 3] = lx * 16 + buffer.x(q, c) - localX;
                        coords[n * 12 + c * 3 + 1] = buffer.y(q, c) - origin.getY();
                        coords[n * 12 + c * 3 + 2] = lz * 16 + buffer.z(q, c) - localZ;
                    }
                    colours[n++] = buffer.colour(q);
                }
            }
        }
        if (n == 0) {
            return;
        }
        TextureAtlasSprite sprite =
                mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(WHITE);
        Quads quads = new Quads(
                Arrays.copyOf(coords, n * 12),
                Arrays.copyOf(colours, n),
                (sprite.getU0() + sprite.getU1()) / 2,
                (sprite.getV0() + sprite.getV1()) / 2);
        event.addRenderer(ctx -> {
            VertexConsumer out = ctx.getOrCreateChunkBuffer(RenderType.translucent());
            PoseStack.Pose pose = ctx.getPoseStack().last();
            for (int q = 0; q < quads.colours.length; q++) {
                for (int c = 0; c < 4; c++) {
                    int i = q * 12 + c * 3;
                    out.addVertex(pose, quads.coords[i], quads.coords[i + 1], quads.coords[i + 2])
                            .setColor(quads.colours[q])
                            .setUv(quads.u, quads.v)
                            .setLight(LightTexture.FULL_BRIGHT)
                            .setNormal(0, 1, 0);
                }
            }
        });
    }

    /** The surface at match-local column ({@code x}, {@code z}): the match's floor where its chunk is not loaded. */
    private static int floor(ClientLevel level, MatchJoin join, int x, int z) {
        int wx = join.originX() + x;
        int wz = join.originZ() + z;
        if (!level.hasChunk(wx >> 4, wz >> 4)) {
            return join.originY();
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
    }

    /**
     * A territory version was completed: marks dirty the world sections whose borders may differ, over the match's
     * height band. Called on the client thread where the territory is applied.
     */
    public static void territoryChanged(ClientTerritory territory, MatchJoin join) {
        Minecraft mc = Minecraft.getInstance();
        TerritoryGrid grid = territory.grid();
        if (mc.level == null || grid == null) {
            return;
        }
        LevelRenderer renderer = mc.levelRenderer;
        int sectionsX = BorderGeometry.sectionsFor(grid.tilesX());
        BitSet touched = BorderGeometry.sectionsTouched(territory.changed(), grid.tilesX(), grid.tilesZ());
        dirty(renderer, touched, sectionsX, join);
        StrategosClient.borderEcho().arm(touched, sectionsX, join);
    }

    static void dirty(LevelRenderer renderer, BitSet touched, int sectionsX, MatchJoin join) {
        int minSy = Math.floorDiv(join.minY(), 16);
        int maxSy = Math.floorDiv(join.maxY(), 16);
        for (int s = touched.nextSetBit(0); s >= 0; s = touched.nextSetBit(s + 1)) {
            int x0 = join.originX() + (s % sectionsX) * 16;
            int z0 = join.originZ() + (s / sectionsX) * 16;
            for (int wz = Math.floorDiv(z0, 16); wz <= Math.floorDiv(z0 + 15, 16); wz++) {
                for (int wx = Math.floorDiv(x0, 16); wx <= Math.floorDiv(x0 + 15, 16); wx++) {
                    for (int sy = minSy; sy <= maxSy; sy++) {
                        renderer.setSectionDirty(wx, sy, wz);
                    }
                }
            }
        }
    }
}
