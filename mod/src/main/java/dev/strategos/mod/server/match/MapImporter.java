package dev.strategos.mod.server.match;

import dev.strategos.core.content.MapManifest;
import dev.strategos.core.content.TerrainHash;
import dev.strategos.core.sim.world.WritePriority;
import dev.strategos.core.sim.world.WriteSite;
import dev.strategos.mod.server.content.MapPackage;
import dev.strategos.mod.server.content.MapTile;
import dev.strategos.mod.server.world.BlockWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * Places a map package into a level (04 §3.3, MAP-01) through the block writer, tile after tile, a few tiles ahead,
 * each tile its own write site at {@code IMPORT} priority; the writer gives it the import budget while no match in the
 * level runs. Each column gets the base block below its lowest stored block. Blocks go in Y order, so sand never waits
 * for its support, and fluids go last, so water never flows into a space a later block fills. The import is done once
 * every write is made and the light engine has no work left (LevelLightEngine.java:38); then the terrain hash of what
 * stands in the level is taken, for the caller to compare with the manifest's.
 */
public final class MapImporter {
    /** Import sites start here, so they never meet a match's own sites. */
    public static final int SITE_BASE = 1 << 20;

    private static final int AHEAD = 65_536;
    private static final Comparator<MapTile.Placed> Y_THEN_X_THEN_Z = Comparator.comparingInt(MapTile.Placed::y)
            .thenComparingInt(MapTile.Placed::x)
            .thenComparingInt(MapTile.Placed::z);

    private final ServerLevel level;
    private final MapPackage pkg;
    private final BlockPos origin;
    private final int match;
    private final BlockWriter writer;
    private final long startNanos = System.nanoTime();
    private final List<MapTile.Placed> fluids = new ArrayList<>();
    private int nextTile;
    private boolean fluidsQueued;
    private @Nullable String hash;
    private long millis = -1;

    /** {@code origin} takes the map's corner and the bottom of its band; {@code match} is 0 for no match. */
    public MapImporter(ServerLevel level, MapPackage pkg, BlockPos origin, int match, BlockWriter writer) {
        this.level = level;
        this.pkg = pkg;
        this.origin = origin.immutable();
        this.match = match;
        this.writer = writer;
    }

    /** One tick of feeding the writer; true once the import is done and its hash taken. */
    public boolean step() {
        if (hash != null) {
            return true;
        }
        MapManifest m = pkg.manifest();
        int tiles = m.tilesX() * m.tilesZ();
        while (nextTile < tiles && writer.pending(level) < AHEAD) {
            queueTile(nextTile++);
        }
        if (nextTile < tiles) {
            return false;
        }
        if (!fluidsQueued) {
            WriteSite site = new WriteSite(match, SITE_BASE + tiles);
            for (MapTile.Placed f : fluids) {
                writer.enqueue(level, site, WritePriority.IMPORT, origin.offset(f.x(), f.y(), f.z()), f.state(), 0);
            }
            fluids.clear();
            fluidsQueued = true;
        }
        for (int t = 0; t <= tiles; t++) {
            if (writer.pending(level, new WriteSite(match, SITE_BASE + t)) > 0) {
                return false;
            }
        }
        if (level.getLightEngine().hasLightWork()) {
            return false;
        }
        hash = terrainHash();
        millis = (System.nanoTime() - startNanos) / 1_000_000;
        return true;
    }

    /** The terrain hash of the placed map, once {@link #step()} returned true. */
    public @Nullable String hash() {
        return hash;
    }

    /** How long the import took, from the start to the hash, or -1 while it runs. */
    public long millis() {
        return millis;
    }

    public MapPackage map() {
        return pkg;
    }

    public BlockPos origin() {
        return origin;
    }

    /** The terrain hash of the map's box in the level now. */
    public String terrainHash() {
        MapManifest m = pkg.manifest();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        var cache = new Object() {
            long key = Long.MIN_VALUE;

            @Nullable LevelChunk chunk;
        };
        return TerrainHash.of(m.sizeX(), m.sizeZ(), m.height(), (x, y, z) -> {
            pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
            long key = ((long) (pos.getX() >> 4) << 32) | ((pos.getZ() >> 4) & 0xFFFF_FFFFL);
            LevelChunk chunk = cache.chunk;
            if (chunk == null || key != cache.key) {
                chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                cache.chunk = chunk;
                cache.key = key;
            }
            return pkg.paletteIndex(chunk.getBlockState(pos));
        });
    }

    private void queueTile(int t) {
        MapManifest m = pkg.manifest();
        int i = t % m.tilesX();
        int j = t / m.tilesX();
        int size = m.tileSize();
        List<MapTile.Placed> stored = pkg.tile(i, j);
        int[] lowest = new int[size * size];
        Arrays.fill(lowest, Integer.MAX_VALUE);
        for (MapTile.Placed b : stored) {
            int column = b.x() * size + b.z();
            lowest[column] = Math.min(lowest[column], b.y());
        }
        List<MapTile.Placed> writes = new ArrayList<>();
        BlockState base = pkg.base();
        if (!base.isAir()) {
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    int top = lowest[x * size + z];
                    for (int y = 0; y < top && top != Integer.MAX_VALUE; y++) {
                        writes.add(new MapTile.Placed(x, y, z, base));
                    }
                }
            }
        }
        for (MapTile.Placed b : stored) {
            if (b.state().getFluidState().isEmpty()) {
                writes.add(b);
            } else {
                fluids.add(new MapTile.Placed(i * size + b.x(), b.y(), j * size + b.z(), b.state()));
            }
        }
        writes.sort(Y_THEN_X_THEN_Z);
        WriteSite site = new WriteSite(match, SITE_BASE + t);
        for (MapTile.Placed b : writes) {
            writer.enqueue(
                    level,
                    site,
                    WritePriority.IMPORT,
                    origin.offset(i * size + b.x(), b.y(), j * size + b.z()),
                    b.state(),
                    0);
        }
    }
}
