package dev.strategos.mod.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.strategos.core.client.camera.CameraRig;
import dev.strategos.core.client.hud.HudRect;
import dev.strategos.core.client.hud.Minimap;
import dev.strategos.core.client.hud.MinimapImage;
import dev.strategos.core.client.pick.View;
import dev.strategos.core.protocol.MatchJoin;
import dev.strategos.core.protocol.MatchNews;
import dev.strategos.mod.client.StrategosClient;
import dev.strategos.mod.client.rts.RtsMode;
import dev.strategos.mod.platform.Ids;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The minimap's terrain (UI-11, D-16): one {@link DynamicTexture} the size of the joined match's space, a pixel a
 * column, built a chunk at a time from the client's loaded blocks by {@link MinimapImage}. A few chunks a tick are
 * rebuilt in turn, so every chunk is looked at every few seconds; a chunk whose pixels changed marks the texture, which
 * is uploaded at most once a second. A chunk not loaded yet stays transparent until it is.
 */
final class RtsMinimap {
    static final ResourceLocation TEXTURE = Ids.id("minimap");

    /** Chunks rebuilt a client tick. */
    private static final int PER_TICK = 4;

    private @Nullable DynamicTexture texture;
    private int matchId = -1;
    private int sizeX;
    private int sizeZ;
    private int[] tileHashes = new int[0];
    private int next;
    private boolean dirty;
    private long uploadedAt = -1_000; // "long ago", without the overflow Long.MIN_VALUE would give a subtraction

    /** Whether the texture holds a match's image. */
    boolean ready() {
        return texture != null;
    }

    int sizeX() {
        return sizeX;
    }

    int sizeZ() {
        return sizeZ;
    }

    /** The pixel of column ({@code x}, {@code z}) as the texture holds it, ARGB; 0 outside or before any. */
    int pixel(int x, int z) {
        DynamicTexture t = texture;
        NativeImage image = t == null ? null : t.getPixels();
        if (image == null || x < 0 || z < 0 || x >= sizeX || z >= sizeZ) {
            return 0;
        }
        return abgr(image.getPixelRGBA(x, z)); // the swap is its own inverse
    }

    /** One client tick: the next chunks rebuilt, the texture uploaded if they changed and a second has passed. */
    void tick(Minecraft mc) {
        MatchJoin join = StrategosClient.match();
        ClientLevel level = mc.level;
        if (join == null || level == null) {
            close(mc);
            return;
        }
        if (join.matchId() != matchId || texture == null) {
            open(mc, join);
        }
        DynamicTexture t = texture;
        NativeImage image = t == null ? null : t.getPixels();
        if (t == null || image == null) {
            return;
        }
        int chunksX = (sizeX + 15) / 16;
        int chunksZ = (sizeZ + 15) / 16;
        MinimapImage.Columns columns = new Columns(level, join);
        for (int k = 0; k < PER_TICK; k++) {
            int c = next;
            next = (next + 1) % (chunksX * chunksZ);
            int cx = c % chunksX;
            int cz = c / chunksX;
            int[] tile = MinimapImage.tile(columns, cx, cz, sizeX, sizeZ);
            int hash = Arrays.hashCode(tile);
            if (hash == tileHashes[c]) {
                continue;
            }
            tileHashes[c] = hash;
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    int x = cx * 16 + dx;
                    int z = cz * 16 + dz;
                    if (x < sizeX && z < sizeZ) {
                        image.setPixelRGBA(x, z, abgr(tile[dz * 16 + dx]));
                    }
                }
            }
            dirty = true;
        }
        long now = level.getGameTime();
        if (dirty && now - uploadedAt >= 20) {
            t.upload();
            dirty = false;
            uploadedAt = now;
        }
    }

    private void open(Minecraft mc, MatchJoin join) {
        close(mc);
        matchId = join.matchId();
        sizeX = join.sizeX();
        sizeZ = join.sizeZ();
        DynamicTexture t = new DynamicTexture(sizeX, sizeZ, true);
        mc.getTextureManager().register(TEXTURE, t);
        texture = t;
        int chunks = ((sizeX + 15) / 16) * ((sizeZ + 15) / 16);
        tileHashes = new int[chunks];
        Arrays.fill(tileHashes, 1); // an empty tile hashes otherwise, so every chunk is written once
        next = 0;
        dirty = true;
        uploadedAt = -1_000;
    }

    private void close(Minecraft mc) {
        if (texture != null) {
            mc.getTextureManager().release(TEXTURE);
            texture = null;
            matchId = -1;
        }
    }

    /** ARGB as NativeImage keeps it: alpha, blue, green, red. */
    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
    }

    /** The client's columns of the joined match: the top block's map colour and height; none where not loaded. */
    private record Columns(ClientLevel level, MatchJoin join) implements MinimapImage.Columns {
        @Override
        public int colour(int x, int z) {
            int wx = join.originX() + x;
            int wz = join.originZ() + z;
            if (!level.hasChunk(wx >> 4, wz >> 4)) {
                return 0;
            }
            BlockPos pos = new BlockPos(wx, height(x, z), wz);
            return level.getBlockState(pos).getMapColor(level, pos).col;
        }

        @Override
        public int height(int x, int z) {
            int wx = join.originX() + x;
            int wz = join.originZ() + z;
            if (!level.hasChunk(wx >> 4, wz >> 4)) {
                return 0;
            }
            return level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
        }
    }

    /**
     * The minimap (UI-11): the terrain image scaled into the HUD's square, buildings and units as dots in their
     * factions' colours, the latest alert as a red ring for its first seconds, and the camera's frame.
     */
    void draw(GuiGraphics g, RtsMode mode, HudPaint paint, long clientTicks, long alertAt) {
        MatchJoin join = StrategosClient.match();
        if (join == null || !ready()) {
            return;
        }
        HudRect r = RtsHud.layout().minimap();
        paint.panel(g, r);
        g.flush(); // the panel's fill is batched: drawn later, it would cover the image
        Minimap m = new Minimap(r, join.sizeX(), join.sizeZ());
        double[] o = m.origin();
        double s = m.scale();
        g.blit(
                RtsMinimap.TEXTURE,
                (int) Math.round(o[0]),
                (int) Math.round(o[1]),
                (int) Math.round(join.sizeX() * s),
                (int) Math.round(join.sizeZ() * s),
                0,
                0,
                join.sizeX(),
                join.sizeZ(),
                join.sizeX(),
                join.sizeZ());
        for (Minimap.Dot d : m.dots(
                StrategosClient.model().units(), StrategosClient.model().buildings(), StrategosClient.templates())) {
            int x0 = (int) Math.floor(d.x());
            int y0 = (int) Math.floor(d.y());
            g.fill(
                    x0,
                    y0,
                    x0 + Math.max(1, (int) Math.round(d.w())),
                    y0 + Math.max(1, (int) Math.round(d.h())),
                    paint.theme.faction(d.faction()));
        }
        MatchNews alert = StrategosClient.model().alert();
        if (alert != null && clientTicks - alertAt < 200 && (clientTicks / 5) % 2 == 0) {
            double[] p = m.toMap(alert.x(), alert.z());
            g.renderOutline((int) p[0] - 4, (int) p[1] - 4, 9, 9, 0xFFFF3030);
        }
        CameraRig rig = mode.rig();
        View view = mode.view();
        if (rig != null && view != null) {
            double[] f = Minimap.frame(view, rig.pose().y() - rig.height(), join.originX(), join.originZ());
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (int k = 0; k < 4; k++) {
                double[] p = m.toMap(
                        Math.max(0, Math.min(join.sizeX(), f[2 * k])),
                        Math.max(0, Math.min(join.sizeZ(), f[2 * k + 1])));
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
            }
            g.renderOutline(
                    (int) minX,
                    (int) minY,
                    Math.max(2, (int) (maxX - minX)),
                    Math.max(2, (int) (maxY - minY)),
                    0xFFFFFFFF);
        }
    }
}
