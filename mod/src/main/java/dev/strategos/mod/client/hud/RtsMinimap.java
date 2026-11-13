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
 * is uploaded at most once a second. A chunk not loaded yet stays transparent until it is. Live construction (UI-20):
 * a building whose {@code building_state} progress or state moved marks the chunks under it dirty, and a dirty chunk is
 * rebuilt ahead of the round, within a second and at most once a second. Pings (UI-20): a ring at each alert and wave
 * muster for three seconds.
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
    private int visionRevision = -1;
    private long[] builtAt = new long[0];
    private final java.util.LinkedHashSet<Integer> dirtyChunks = new java.util.LinkedHashSet<>();
    private final java.util.Map<Integer, Long> buildingSeen = new java.util.HashMap<>();
    private final java.util.ArrayList<long[]> pings = new java.util.ArrayList<>(); // x, z, start tick
    private int alertsSeen;
    private int wavesSeen;

    /** How long a ping shows, and how soon a dirty chunk may be rebuilt again: ticks. */
    static final int PING_TICKS = 60;

    static final int REBUILD_EVERY = 20;

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
        var model = StrategosClient.model();
        int rev = model.visionRevision();
        if (rev != visionRevision) {
            Arrays.fill(tileHashes, 1); // fog changed: every chunk must be rewritten
            visionRevision = rev;
        }
        MinimapImage.Columns columns = new Columns(level, join);
        MinimapImage.Fog fog = MinimapImage.fromVision(
                model.visibleBits(), model.exploredBits(), model.visionCellsX(), model.visionCellsZ());
        long now = level.getGameTime();
        markConstruction(model.buildings(), chunksX, chunksZ);
        notePings(model, now);
        int budget = PER_TICK;
        for (java.util.Iterator<Integer> it = dirtyChunks.iterator(); it.hasNext() && budget > 0; ) {
            int c = it.next();
            if (now - builtAt[c] < REBUILD_EVERY) {
                continue; // built within the second: it waits its turn
            }
            it.remove();
            rebuild(image, columns, fog, c, chunksX, now);
            budget--;
        }
        for (int k = 0; k < budget; k++) {
            int c = next;
            next = (next + 1) % (chunksX * chunksZ);
            rebuild(image, columns, fog, c, chunksX, now);
        }
        if (dirty && now - uploadedAt >= 20) {
            t.upload();
            dirty = false;
            uploadedAt = now;
        }
    }

    private void rebuild(
            NativeImage image, MinimapImage.Columns columns, MinimapImage.Fog fog, int c, int chunksX, long now) {
        int cx = c % chunksX;
        int cz = c / chunksX;
        builtAt[c] = now;
        int[] tile = MinimapImage.tile(columns, fog, cx, cz, sizeX, sizeZ);
        int hash = Arrays.hashCode(tile);
        if (hash == tileHashes[c]) {
            return;
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

    /** A building whose state or progress moved since the last tick marks the chunks round its corner dirty. */
    private void markConstruction(
            java.util.List<dev.strategos.core.protocol.BuildingState.Entry> buildings, int chunksX, int chunksZ) {
        for (dev.strategos.core.protocol.BuildingState.Entry b : buildings) {
            long seen = ((long) b.state() << 32) | (b.progress() & 0xFFFFFFFFL);
            Long before = buildingSeen.put(b.handle(), seen);
            if (before == null || before == seen) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) { // the footprint reaches into the chunks round its corner
                for (int dz = -1; dz <= 1; dz++) {
                    int cx = (b.x() >> 4) + dx;
                    int cz = (b.z() >> 4) + dz;
                    if (cx >= 0 && cz >= 0 && cx < chunksX && cz < chunksZ) {
                        dirtyChunks.add(cz * chunksX + cx);
                    }
                }
            }
        }
    }

    /** New alerts and wave musters since the last tick become pings. */
    private void notePings(dev.strategos.core.client.model.ClientMatchModel model, long now) {
        if (model.alerts() > alertsSeen) {
            alertsSeen = model.alerts();
            MatchNews a = model.alert();
            if (a != null) {
                pings.add(new long[] {Math.round(a.x()), Math.round(a.z()), now});
            }
        }
        if (model.waves() > wavesSeen) {
            wavesSeen = model.waves();
            MatchNews w = model.wave();
            if (w != null) {
                pings.add(new long[] {Math.round(w.x()), Math.round(w.z()), now});
            }
        }
        pings.removeIf(p -> now - p[2] >= PING_TICKS);
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
        builtAt = new long[chunks];
        Arrays.fill(builtAt, -1_000);
        dirtyChunks.clear();
        buildingSeen.clear();
        pings.clear();
        next = 0;
        dirty = true;
        uploadedAt = -1_000;
        visionRevision = -1;
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
        ClientLevel level = Minecraft.getInstance().level;
        long now = level == null ? 0 : level.getGameTime();
        for (long[] ping : pings) { // UI-20: a ring growing out of each alert and muster for three seconds
            double[] p = m.toMap((double) ping[0], (double) ping[1]);
            int radius = 2 + (int) ((now - ping[2]) * 10 / PING_TICKS);
            g.renderOutline((int) p[0] - radius, (int) p[1] - radius, 2 * radius + 1, 2 * radius + 1, 0xFFFFB030);
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
